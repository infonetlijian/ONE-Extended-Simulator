/* 
 * Copyright 2016 University of Science and Technology of China , Infonet Lab
 * Written by LiJian.
 */
package routing;  

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import routing.NetGridRouter.GridNeighbors.GridCell;
import movement.MovementModel;
import movement.SatelliteMovement;
import util.Tuple;
import core.Connection;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.NetworkInterface;
import core.Settings;
import core.SettingsError;
import core.SimClock;
import core.SimError;

public class NetGridRouter extends ActiveRouter{
	
	/** write the routing path into the message header or not -setting id ({@value})*/
	public static final String MSG_PATHLABEL = "msgPathLabel"; 
	/** Group name in the group -setting id ({@value})*/
	public static final String GROUPNAME_S = "Group";
	/** interface name in the group -setting id ({@value})*/
	public static final String INTERFACENAME_S = "Interface";
	/** transmit range -setting id ({@value})*/
	public static final String TRANSMIT_RANGE_S = "transmitRange";
	/** grid update mode -setting id ({@value})*/
	public static final String GRIDUPDATEOPTION_S = "gridUpdateOption";
	/** write the routing path into the message header */
	public static final String MSG_ROUTERPATH = "routerPath"; 

	/** light speed??approximate 3*10^8m/s */
	private static final double SPEEDOFLIGHT = 299792458;
	
    /** indicate the transmission radius of each satellite */
    private static double transmitRange;
    /** label indicates that routing path can contain in the message or not */
    private static boolean msgPathLabel;
    /** label indicates that the static routing parameters are set or not */
    private static boolean initLabel = false;
    /** label indicates the grid topology should be updated online or offline */
    private static String gridUpdateOption;
    /** to make the random choice */
    private static Random random;	
    
	/**???ݻ?????????????·?????????????洢?????????ĵ???Ŀ?Ľڵ???????·??????ѡ????·ʱֱ??ʹ??**/
    /** the netgrid router table comes from routing algorithm */
	private HashMap<DTNHost, List<Tuple<List<Integer>, Boolean>>> multiPathFromNetgridTable = new HashMap<DTNHost, List<Tuple<List<Integer>, Boolean>>>();	
	/** maintain the earliest arrival time to other nodes */
    private HashMap<DTNHost, Double> arrivalTime = new HashMap<DTNHost, Double>();
    /** the router table comes from routing algorithm */
    private HashMap<DTNHost, List<Tuple<Integer, Boolean>>> routerTable = new HashMap<DTNHost, List<Tuple<Integer, Boolean>>>();
    /** label indicates that routing algorithm has been executed or not at this time */
    private boolean routerTableUpdateLabel;
    /** the netgrid object which is used in the routing algorithm */
	private GridNeighbors GN;

    /** to indicates the final hop */
	private boolean finalHopLabel = false;
	/** to indicates the final connection */
	private Connection finalHopConnection = null;

	/** used as priori information for Walker constellation */
	public List<DTNHost> neighborPlaneHosts = new ArrayList<DTNHost>();//??ͬ????ƽ???????????ھӽڵ?
	public List<DTNHost> neighborHostsInSamePlane = new ArrayList<DTNHost>();//???ڹ???ƽ???ڵ??????ھӽڵ?
	
	/** Queue mode for sending messages */
	protected int sendQueueMode;
	
	public static final String SEND_QUEUE_MODE_S = "sendQueue";
	
	public NetGridRouter(Settings s){
		super(s);
	}

	protected NetGridRouter(NetGridRouter r) {
		super(r);
		this.GN = new GridNeighbors(this.getHost());
	}

	@Override
	public MessageRouter replicate() {
		return new NetGridRouter(this);
	}
	
    @Override
    public void init(DTNHost host, List<MessageListener> mListeners) {
        super.init(host, mListeners);
        if (!initLabel){
        	random = new Random();
            Settings setting = new Settings(INTERFACENAME_S);
            transmitRange = setting.getInt(TRANSMIT_RANGE_S);
            setting.setNameSpace(GROUPNAME_S);
            msgPathLabel = setting.getBoolean(MSG_PATHLABEL);
            gridUpdateOption = setting.getSetting(GRIDUPDATEOPTION_S);//???????ļ??ж?ȡ???ã??ǲ????????й????в??ϼ????????????ķ?ʽ??????ͨ????ǰ???????????洢?????ڵ??Ĺ?????Ϣ
            initLabel = true;
        }
    }
	/**
	 * ִ??·?ɵĳ?ʼ??????
	 */
	public void initialzation(){
		GN.setHost(this.getHost());//Ϊ??ʵ??GN??Router?Լ?Host֮???İ󶨣????޸ģ?????????????????????????????????????????????????????????????????????????????????????????????????
		initInterSatelliteNeighbors();//??ʼ????¼?ڵ???ͬһ???????ڵ????ڽڵ㣬?Լ?????ƽ?????ھ?
		//this.GN.initializeGridLocation();//??ʼ????ǰ??????????
	}	

	@Override
	public void changedConnection(Connection con){
		super.changedConnection(con);

		if (!con.isUp()){
			if(con.isTransferring()){
				if (con.getOtherNode(this.getHost()).getRouter().isIncomingMessage(con.getMessage().getId()))
					con.getOtherNode(this.getHost()).getRouter().removeFromIncomingBuffer(con.getMessage().getId(), this.getHost());
				super.addToMessages(con.getMessage(), false);//??????Ϊ??·?ж϶???ʧ????Ϣ?????·Żط??ͷ??Ķ????У?????ɾ???Է??ڵ???incoming??Ϣ
			}
		}
	}

	/**
	 * ·?ɸ??£?ÿ?ε???·?ɸ???ʱ????????
	 */
	@Override
	public void update() {
		super.update();
		
		/**??̬???????ڹ???ƽ???ڵ??ھӽڵ??б?(??Ϊ?ڱ?Ե????ƽ??ʱ????????)**/
		List<DTNHost> neiList = getNeighbors(this.getHost(), SimClock.getTime());//ͨ?????????жϵ??ھӣ??????ܵ???·?жϵ?Ӱ??	
		neighborPlaneHosts.clear();//???????ڹ???ƽ???ڵ??ھӽڵ??б?(?ڱ?Ե????ƽ??ʱ????????)
		updateInterSatelliteNeighbors(neiList);//??̬???????ڹ???ƽ???ڵ??ھӽڵ??б?

		List<Connection> connections = this.getConnections();  //ȡ???????ھӽڵ?
		List<Message> messages = new ArrayList<Message>(this.getMessageCollection());
				
		if (isTransferring()) {//?ж???·?Ƿ???ռ??
			return; // can't start a new transfer
		}
		if (connections.size() > 0){//???ھ?ʱ??Ҫ????hello??????Э??
			//helloProtocol();//ִ??hello????ά??????
		}
		if (!canStartTransfer())//?Ƿ????ֽܽڵ???????Ϣ??Ҫ????
			return;

		this.multiPathFromNetgridTable.clear();
		routerTableUpdateLabel = false;
		if (messages.isEmpty())
			return;
		
		//?ж?????Ϊ?Ƚ??ȳ?ģʽ????????????
		Settings s = new Settings("Group");
		if (s.contains(SEND_QUEUE_MODE_S)) {
			this.sendQueueMode = s.getInt(SEND_QUEUE_MODE_S);
			if (sendQueueMode < 1 || sendQueueMode > 2) {
				throw new SettingsError("Invalid value for " + 
						s.getFullPropertyName(SEND_QUEUE_MODE_S));
			}
		}
		else {
			sendQueueMode = Q_MODE_RANDOM;
		}
		// FIFO, sort the messages
		if(sendQueueMode == 2){
	        /** sort the messages to transmit */
	        List<Message> messageList = this.CollectionToList(this.getMessageCollection());
	        List<Message> sortedMessages = sortByQueueMode(messageList);
			for (Message msg : sortedMessages){	//???Է??Ͷ?????????Ϣ	
				if (findPathToSend(msg, connections) == true)
					return;
			}
		} 
		
		else{
			for (Message msg : messages){	//???Է??Ͷ?????????Ϣ	
				if (findPathToSend(msg, connections) == true)
					return;
			}
		}
	}
	
    /** transform the Collection to List
     * @param messages 
     * @return
     */
    
    public List<Message> CollectionToList(Collection<Message> messages){
    	List<Message> forMsg = new ArrayList<Message>();
        for (Message msg : messages) {	//???Է??Ͷ?????????Ϣ
        	forMsg.add(msg);
        }
    	return forMsg;
    }
	
	
	/**
	 * Calculates all neighbors of specific host at specific time
	 * @param host
	 * @param time
	 * @return all neighbors of specific host
	 */
	public List<DTNHost> getNeighbors(DTNHost host, double time){
		double updateInterval = (new Settings("Scenario")).getDouble("updateInterval");
		int num = (int)((time-SimClock.getTime())/updateInterval);
		time = SimClock.getTime()+num*updateInterval;
		
		List<DTNHost> neiHost = new ArrayList<DTNHost>();//?ھ??б?
		
		HashMap<DTNHost, Coord> loc = new HashMap<DTNHost, Coord>();
		loc.clear();
		
		/**ԭ???Ĵ????У????Ż????ƣ???????ʵ?ʣ??ʶ?ɾ??**/
		/*
		if (!(time == SimClock.getTime())){
			for (DTNHost h : hosts){//????ָ??ʱ??ȫ?ֽڵ???????
				//location.my_Test(time, 0, h.getParameters());
				//Coord xyz = new Coord(location.getX(), location.getY(), location.getZ());
				Coord xyz = h.getCoordinate(time);
				loc.put(h, xyz);//??¼ָ??ʱ??ȫ?ֽڵ???????
			}
		}
		else{
			for (DTNHost h : hosts){//????ָ??ʱ??ȫ?ֽڵ???????
				loc.put(h, h.getLocation());//??¼ָ??ʱ??ȫ?ֽڵ???????
			}
		}*/
		
		/**ʵʱ????ȫ???ڵ??????깹??????ͼ**/
		for (DTNHost h : getHosts()){//????ָ??ʱ??ȫ?ֽڵ???????
			//location.my_Test(time, 0, h.getParameters());
			//Coord xyz = new Coord(location.getX(), location.getY(), location.getZ());
			/**???ݹ???ģ?ͣ?ʵʱ?????ڵ㵱ǰ??λ?ã??????ٶȽ???**/
			//Coord xyz = h.getCoordinate(time);
			/**ֱ?ӻ?ȡ??ǰ?Ľڵ?λ?ã??򻯼??????̣????߷????????ٶ?**/
			Coord xyz = h.getLocation();
			/**ֱ?ӻ?ȡ??ǰ?Ľڵ?λ?ã??򻯼???????**/
			loc.put(h, xyz);//??¼ָ??ʱ??ȫ?ֽڵ???????
		}
		
		Coord myLocation = loc.get(host);
		for (DTNHost h : getHosts()){//?ٷֱ𼰼???
			if (h == host)
				continue;
			if (JudgeNeighbors(myLocation, loc.get(h)) == true){
				double distance = myLocation.distance(loc.get(h));
				//System.out.println(host+"  locate  "+myLocation+" to "+h + "  the distance is: " + distance);
				neiHost.add(h);
			}
		}
		//System.out.println(host+" neighbor: "+neiHost+" time: "+time);

		return neiHost;
	}
	/**
	 * ??Coord?????????о???????
	 * @param c1
	 * @param c2
	 * @return
	 */
	public boolean JudgeNeighbors(Coord c1,Coord c2){

		double distance = c1.distance(c2);
		if (distance <= this.transmitRange)
			return true;
		else
			return false;
	}	

	/**
	 * ????·?ɱ???Ѱ??·????????ת????Ϣ
	 * @param msg
	 * @param connections
	 * @param msgPathLabel
	 * @return
	 */
	public boolean findPathToSend(Message msg, List<Connection> connections){
		if (msgPathLabel == true){//????????????Ϣ??д??·????Ϣ
			if (msg.getProperty(MSG_ROUTERPATH) == null){//ͨ????ͷ?Ƿ???д??·????Ϣ???ж??Ƿ???Ҫ????????·??(ͬʱҲ??????Ԥ???Ŀ???)
				Tuple<Message, Connection> t = 
						findPathFromRouterTabel(msg, connections, msgPathLabel);
				return sendMsg(t);
			}
			else{//???????м̽ڵ㣬?ͼ?????Ϣ??????·????Ϣ
				Tuple<Message, Connection> t = 
						findPathFromMessage(msg);
				if (t == null){
					//msg.removeProperty(MSG_ROUTERPATH);
					throw new SimError("??ȡ·????Ϣʧ?ܣ?");	
				}						
				return sendMsg(t);
			}
		}else{//????????Ϣ??д??·????Ϣ??ÿһ??????Ҫ???¼???·??
			Tuple<Message, Connection> t = 
					findPathFromRouterTabel(msg, connections, msgPathLabel);//??????????Ϣ˳????·?????????Է???
			return sendMsg(t);
		}
	}
	/**
	 * ͨ????ȡ??Ϣmsgͷ??????·????Ϣ??????ȡ·??·????????ʧЧ??????Ҫ??ǰ?ڵ????¼???·??
	 * @param msg
	 * @return
	 */
	public Tuple<Message, Connection> findPathFromMessage(Message msg){
		assert msg.getProperty(MSG_ROUTERPATH) != null : 
			"message don't have routerPath";//?Ȳ鿴??Ϣ??û??·????Ϣ???????оͰ???????·????Ϣ???ͣ?û????????·?ɱ????з???
		List<Tuple<Integer, Boolean>> routerPath = (List<Tuple<Integer, Boolean>>)msg.getProperty(MSG_ROUTERPATH);
		
		int thisAddress = this.getHost().getAddress();
		//assert msg.getTo().getAddress() != thisAddress : "???ڵ?????Ŀ?Ľڵ㣬???մ??????̴???";
		int nextHopAddress = -1;
		

		//System.out.println(this.getHost()+"  "+msg+" "+routerPath);
		boolean waitLable = false;
		for (int i = 0; i < routerPath.size(); i++){
			if (routerPath.get(i).getKey() == thisAddress){
				/**?鿴?Ƿ?д????·??????**/
				if (routerPath.size() == i + 1){
					if (msg.getTo() != this.getHost()){
						this.receiveMessage(msg, msg.getFrom());
						return null;
					}
					else
						return findPathFromRouterTabel(msg, this.getConnections(), msgPathLabel);
				}
				nextHopAddress = routerPath.get(i+1).getKey();//?ҵ???һ???ڵ???ַ
				waitLable = routerPath.get(i+1).getValue();//?ҵ???һ???Ƿ???Ҫ?ȴ??ı?־λ
				break;//????ѭ??
			}
		}
		
	      if (nextHopAddress > -1) {
	    	  Connection nextCon = NetgridMultiPathMatchingProcess(nextHopAddress);//ͨ??ͬһ?????к??ж????ڵ???????ʱ?????Բ??ö?·??
	            //the routing path in the message header could be invaild
	            if (nextCon == null) {
	                if (!waitLable) {
	                    //msg.removeProperty(MSG_ROUTERPATH);
	                    //try to re-routing
	                    Tuple<Message, Connection> t =
	                            findPathFromRouterTabel(msg, this.getConnections(), true);
	                    return t;
	                }
	            } else {
	                Tuple<Message, Connection> t = new
	                        Tuple<Message, Connection>(msg, nextCon);
	                return t;
	            }
	        }
		return null;	
	}

	/**
	 * ͨ??ͬһ?????к??ж????ڵ???????ʱ?????Բ??ö?·????ͨ???˺????ҵ??˶?·??
	 * @param routerPath
	 * @return
	 */
	public Connection NetgridMultiPathMatchingProcess(int hostAddress){
		DTNHost firstHop = this.findHostByAddress(hostAddress);
		GridCell firstGridCell = this.DTNHostToGridCell.get(firstHop);
		
		if (this.GridCellhasMultiDTNHosts.containsKey(firstGridCell) && this.GridCellhasMultiDTNHosts.get(firstGridCell).size() > 1){
			
			List<DTNHost> multiHostsList = new ArrayList<DTNHost>(this.GridCellhasMultiDTNHosts.get(firstGridCell));
			DTNHost selectedHost;
			Connection con = null;
			for (int i = 0; i < 1;){
				//System.out.println(multiHostsList + "  " + this.GridCellhasMultiDTNHosts.get(firstGridCell));
				if (multiHostsList.size() == 1)
					return findConnection(hostAddress);//ȡ??һ???Ľڵ???ַ
				if (multiHostsList.isEmpty() || multiHostsList.size() <= 0){
					return con;
				}				
				//ע??Random.nextInt(n)?????????ص?ֵ????[0,n)֮?䣬????????n
				selectedHost = multiHostsList.get(Math.abs(this.random.nextInt(multiHostsList.size())));
				
				con = findConnection(selectedHost.getAddress());
				if (con != null)
					return con;
				else
					multiHostsList.remove(selectedHost);
			}
			return con;
		}
		else
			return findConnection(hostAddress);//ȡ??һ???Ľڵ???ַ
	}
	/**
	 * ͨ??????·?ɱ????ҵ???ǰ??ϢӦ??ת??????һ???ڵ㣬???Ҹ???Ԥ?????þ????˼????õ???·????Ϣ?Ƿ???Ҫд????Ϣmsgͷ??????
	 * @param message
	 * @param connections
	 * @param msgPathLabel
	 * @return
	 */
	public Tuple<Message, Connection> findPathFromRouterTabel(Message message, List<Connection> connections, boolean msgPathLabel){
		
		if (updateRouterTable(message) == false){//?ڴ???֮ǰ???ȸ???·?ɱ?
			return null;//??û?з???˵??һ???ҵ??˶?Ӧ·??
		}
		List<Tuple<Integer, Boolean>> routerPath = this.routerTable.get(message.getTo());
		
		if (msgPathLabel == true){//????д??·????Ϣ??־λ?棬??д??·????Ϣ
			message.updateProperty(MSG_ROUTERPATH, routerPath);
		}
				
		//Connection path = findConnection(routerPath.get(0).getKey());//ȡ??һ???Ľڵ???ַ
		
		/**ȷ??????һ??ֱ???ʹ?**/
		if (finalHopLabel == true){
			Tuple<Message, Connection> t = new Tuple<Message, Connection>(message, finalHopConnection);//?ҵ?????һ???ڵ???????
			return t;
		}
		
		Connection path = NetgridMultiPathMatchingProcess(routerPath.get(0).getKey());//ͨ??ͬһ?????к??ж????ڵ???????ʱ?????Բ??ö?·??
		
		if (path != null){
			Tuple<Message, Connection> t = new Tuple<Message, Connection>(message, path);//?ҵ?????һ???ڵ???????
			return t;
		}
		else{			
			
			if (routerPath.get(0).getValue()){
				
				return null;
				//DTNHost nextHop = this.getHostFromAddress(routerPath.get(0).getKey()); 
				//this.busyLabel.put(message.getId(), startTime);//????һ???ȴ?
			}
			else{
//				throw new SimError("No such connection: "+ routerPath.get(0) + 
//						" at routerTable " + this);		
				this.routerTable.remove(message.getTo());	
				return null;

			}
		}
	}

	/**
	 * ?ɽڵ???ַ?ҵ???Ӧ?Ľڵ?DTNHost
	 * @param address
	 * @return
	 */
	public DTNHost findHostByAddress(int address){
		for (DTNHost host : getHosts()){
			if (host.getAddress() == address)
				return host;
		}
		return null;
	}
	/**
	 * ????һ???ڵ???ַѰ?Ҷ?Ӧ???ھ?????
	 * @param address
	 * @return
	 */
	public Connection findConnectionByAddress(int address){
		for (Connection con : this.getHost().getConnections()){
			if (con.getOtherNode(this.getHost()).getAddress() == address)
				return con;
		}
		return null;
	}

	/**
	 * ????·?ɱ???????1????????????·??·????2??????ȫ??Ԥ??
	 * @param m
	 * @return
	 */
	public boolean updateRouterTable(Message msg){
		gridSearch(msg);
		
		if (this.routerTable.containsKey(msg.getTo())){//Ԥ??Ҳ?Ҳ???????Ŀ?Ľڵ???·??????·??ʧ??		
			//System.out.println("Ѱ·?ɹ???????    "+" Path length:  "+routerTable.get(msg.getTo()).size()+" routertable size: "+routerTable.size()+" Netgrid Path:  "+routerTable.get(msg.getTo()));
			return true;//?ҵ???·??
		}else{
			//System.out.println("Ѱ·ʧ?ܣ?????");
			return false;
		}
	}
	
	/**
	 * ð??????
	 * @param distanceList
	 * @return
	 */
	public List<Tuple<DTNHost, Double>> sort(List<Tuple<DTNHost, Double>> distanceList){
		for (int j = 0; j < distanceList.size(); j++){
			for (int i = 0; i < distanceList.size() - j - 1; i++){
				if (distanceList.get(i).getValue() > distanceList.get(i + 1).getValue()){//??С???󣬴???ֵ???ڶ????Ҳ?
					Tuple<DTNHost, Double> var1 = distanceList.get(i);
					Tuple<DTNHost, Double> var2 = distanceList.get(i + 1);
					distanceList.remove(i);
					distanceList.remove(i);//ע?⣬һ??ִ??remove֮????????List?Ĵ?С?ͱ??ˣ?????ԭ??i+1??λ?????ڱ?????i
					//ע??˳??
					distanceList.add(i, var2);
					distanceList.add(i + 1, var1);
				}
			}
		}
		return distanceList;
	}

	private HashMap<GridCell, DTNHost> GridCellToDTNHosts = new HashMap<GridCell, DTNHost>();//??¼?е????ڵ???????
	private HashMap<DTNHost, GridCell> DTNHostToGridCell = new HashMap<DTNHost, GridCell>();
	private HashMap<GridCell, List<DTNHost>> GridCellhasMultiDTNHosts = new HashMap<GridCell, List<DTNHost>>();//??¼?ж????ڵ???????
	/**
	 * ???¼?¼??????DTNHost?ڵ???ϵ??
	 */
	public void updateRelationshipofGridsAndDTNHosts(){
		DTNHostToGridCell.clear();
		GridCellToDTNHosts.clear();
		
		/**ȫ?ֽڵ?????һ??**/
		for (DTNHost h : getHosts()){
			if (h == null)
				throw new SimError("null");
			GridCell Netgrid = GN.getGridCellFromCoordNow(h);
			if (Netgrid == null)
				throw new SimError("null");
			DTNHostToGridCell.put(h, Netgrid);
	
			if (!GridCellToDTNHosts.containsKey(Netgrid)){
				GridCellToDTNHosts.put(Netgrid, h);
			}
			else{
				if (GridCellhasMultiDTNHosts.containsKey(Netgrid)){
					List<DTNHost> hostsList = GridCellhasMultiDTNHosts.get(Netgrid);
					hostsList.add(h);
					GridCellhasMultiDTNHosts.put(Netgrid, hostsList);
				}
				else{
					List<DTNHost> hostsLists = new ArrayList<DTNHost>();
					hostsLists.add(GridCellToDTNHosts.get(Netgrid));
					hostsLists.add(h);
					GridCellhasMultiDTNHosts.put(Netgrid, hostsLists);
				}
			}
		}
		for (GridCell c : GridCellhasMultiDTNHosts.keySet()){//??????GridCellToDTNHosts?б????޳??ж????ڵ???????
			GridCellToDTNHosts.remove(c);
		}	
		//System.out.println(GridCellToDTNHosts.size()+" "+GridCellToDTNHosts + " \n  " + DTNHostToGridCell.size()+"  "
		//		+DTNHostToGridCell + "  \n  " +GridCellhasMultiDTNHosts.size()+" "+GridCellhasMultiDTNHosts);
	}
	
    /**
     * Return current network topology in forms of temporal graph
     */
    public HashMap<DTNHost, List<DTNHost>> globalNetGridCaluculation() {
        HashMap<DTNHost, GridCell> locationRecord = new HashMap<DTNHost, GridCell>();
        HashMap<GridCell, List<DTNHost>> inclusionRelation = new HashMap<GridCell, List<DTNHost>>();
        HashMap<DTNHost, List<DTNHost>> topologyInfo = new HashMap<DTNHost, List<DTNHost>>();

        double radius = transmitRange;//Represent communication Radius
        //Get satellite movement model which store orbit-info of all satellites in the network
        SatelliteMovement movementModel = ((SatelliteMovement) this.getHost().getMovementModel());

        //Calculate the current coordinate of all satellite nodes in the network
        for (DTNHost h : movementModel.getHosts()) {
        	GridCell gc = GN.cellFromCoord(movementModel.getCoordinate(h, SimClock.getTime()));
            locationRecord.put(h, gc);
            
            if (inclusionRelation.containsKey(gc)){
            	inclusionRelation.get(gc).add(h);//add new host belongs to this GridCell
            }
            else{
            	List<DTNHost> hosts = new ArrayList<DTNHost>();
            	hosts.add(h);
            	inclusionRelation.put(gc, hosts);
            }
        }

        //Calculate links between each two satellite nodes
        for (DTNHost h : movementModel.getHosts()) {
            GridCell gc = locationRecord.get(h);
            List<GridCell> neighborList = GN.getNeighborCells(gc.getNumber()[0], gc.getNumber()[1], gc.getNumber()[2]);
            for (GridCell gridCell: neighborList){
            	if (topologyInfo.get(h) == null)
                    topologyInfo.put(h, new ArrayList<DTNHost>());
            	if (inclusionRelation.containsKey(gridCell))
            		topologyInfo.get(h).addAll(inclusionRelation.get(gridCell));
            }
        }
        return topologyInfo;
    }
	/**
	 * ????·???㷨??????̰??ѡ?????ʽ??б??????ҳ?????Ŀ?Ľڵ???????·??
	 * @param msg
	 */
	public void gridSearch(Message msg){		
		this.finalHopLabel = false;
		this.finalHopConnection = null;
		
		if (routerTableUpdateLabel == true)//routerTableUpdateLabel == true???????˴θ???·?ɱ??Ѿ????¹??ˣ????Բ?Ҫ?ظ?????
			return;
		this.routerTable.clear();
		this.arrivalTime.clear();
	
		if (GN.isHostsListEmpty()){
			GN.setHostsList(getHosts());
		}
		
		HashMap<DTNHost, List<DTNHost>> topologyInfo;	
		topologyInfo = globalNetGridCaluculation();
		switch (gridUpdateOption){
		case "onlineOrbitCalculation":
			
			break;
		case "preOrbitCalculation"://ͨ????ǰ???????????洢?????ڵ??Ĺ?????Ϣ???Ӷ????й????в??ٵ??ù??????㺯????Ԥ??????ͨ????????Ԥ??
			/**!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!???µ?ʱ???δ??޸?!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!**/
			//GN.updateNetGridInfo_without_OrbitCalculation(this.RoutingTimeNow);//ʵ?ʷ???ʱ?ã????ڶ?ȡ???ȼ????õ???????
			GN.updateNetGridInfo_without_OrbitCalculation_without_gridTable();//?ӿ??????????ã?ֱ?Ӷ?ȡ???еĽڵ?????ֵ??Ȼ??ת???ɶ?Ӧ????????
			updateRelationshipofGridsAndDTNHosts();//??Ҫ????gridTable????֮??
			//GN.updateGrid_without_OrbitCalculation(this.RoutingTimeNow);//??????????
			/**!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!**/
			break;
		}
		/**ȫ???Ĵ??????ʼٶ?Ϊһ????**/
		double transmitSpeed = this.getHost().getInterface(1).getTransmitSpeed();
		
		/**??????·??̽?⵽??һ???ھ????񣬲?????·?ɱ?**/
		List<DTNHost> searchedSet = new ArrayList<DTNHost>();
		List<DTNHost> sourceSet = new ArrayList<DTNHost>();
		sourceSet.add(this.getHost());//??ʼʱֻ??Դ?ڵ???
		searchedSet.add(this.getHost());//??ʼʱֻ??Դ?ڵ?
		
		for (Connection con : this.getHost().getConnections()){//??????·??̽?⵽??һ???ھӣ???????·?ɱ?
			DTNHost neiHost = con.getOtherNode(this.getHost());
			sourceSet.add(neiHost);//??ʼʱֻ?б??ڵ?????·?ھ?		
			Double time = SimClock.getTime() + msg.getSize()/this.getHost().getInterface(1).getTransmitSpeed();
			List<Tuple<Integer, Boolean>> path = new ArrayList<Tuple<Integer, Boolean>>();
			Tuple<Integer, Boolean> hop = new Tuple<Integer, Boolean>(neiHost.getAddress(), false);
			path.add(hop);//ע??˳??
			arrivalTime.put(neiHost, time);
			routerTable.put(neiHost, path);
			
			if (msg.getTo() == neiHost){//һ?????ھӽڵ㣬ֱ?ӷ???
				finalHopLabel = true;
				finalHopConnection = con;
//				System.out.println(msg+" through "+finalHopConnection+"  to "+msg.getTo());
				//GridCell desNetgrid = GN.getGridCellFromCoordNow(msg.getTo());
				//System.out.println(desNetgrid+"  "+neighborNetgrid+"  "+SimClock.getTime());
				return;
			}
		}
		
		/**??????·??̽?⵽??һ???ھ????񣬲?????·?ɱ?**/		
		int iteratorTimes = 0;
		int size = getHosts().size();
		boolean updateLabel = true;
		boolean predictLable = false;

		arrivalTime.put(this.getHost(), SimClock.getTime());//??ʼ??????ʱ??
		
		/**???ȼ????У?????????**/
		List<Tuple<DTNHost, Double>> PriorityQueue = new ArrayList<Tuple<DTNHost, Double>>();
		/**???ȼ????У?????????**/
		
		while(true){//Dijsktra?㷨˼?룬ÿ??????ȫ?֣???ʱ????С?ļ???·?ɱ?????֤·?ɱ?????Զ??ʱ????С??·??
			if (iteratorTimes >= size )//|| updateLabel == false)
				break; 
			updateLabel = false;
			
			for (DTNHost c : sourceSet){															
				//List<DTNHost> neighborHostsList = GN.getNeighborsHostsNow(GN.cellFromCoord(c.getLocation()));//??ȡԴ??????host?ڵ????ھӽڵ?(??ǰ???ھ?????)
				List<DTNHost> neighborHostsList = topologyInfo.get(c);
				/**????ͬһ?????ڵ????ڽڵ㣬?Լ????ڹ????ڵ??????????ڽڵ?**/
				neighborHostsList.removeAll(((NetGridRouter)c.getRouter()).neighborHostsInSamePlane);//ȥ?ظ?
				neighborHostsList.addAll(((NetGridRouter)c.getRouter()).neighborHostsInSamePlane);//????ͬһ?????ڵ????ڽڵ?
				if (!((NetGridRouter)c.getRouter()).neighborPlaneHosts.isEmpty()){
					neighborHostsList.removeAll(((NetGridRouter)c.getRouter()).neighborPlaneHosts);//ȥ?ظ?
					neighborHostsList.addAll(((NetGridRouter)c.getRouter()).neighborPlaneHosts);//???????ڹ????ڵ??????????ڽڵ?
				}
				//System.out.println("RoutingHost and time :  "+this.getHost()+this.RoutingTimeNow+"  thisHostGrid:  "+thisHostGrid  +"  SourceNetgird:  "+c+"  contains:  "+GN.getHostsFromNetgridNow(c, this.RoutingTimeNow)+"  NeighborNetgrid:  "+neighborNetgridsList.keySet()+" contains: "+neighborNetgridsList.values()+"  sourceSet:  "+sourceSet);
										
				/**?ж??Ƿ??Ѿ???????????Դ???񼯺??е?????**/
				if (searchedSet.contains(c) || neighborHostsList == null)
					continue;				
				searchedSet.add(c);
				
				for (DTNHost eachNeighborHost : neighborHostsList){//startTime.keySet()?????????е??ھӽڵ㣬????δ?????ھӽڵ?
					if (sourceSet.contains(eachNeighborHost))//ȷ??????ͷ
						continue;
				
					double time = arrivalTime.get(c) + msg.getSize()/transmitSpeed;
					
					/**????·????Ϣ**/
					List<Tuple<Integer, Boolean>> path = new ArrayList<Tuple<Integer, Boolean>>();
					if (this.routerTable.containsKey(c))
						path.addAll(this.routerTable.get(c));
					Tuple<Integer, Boolean> thisHop = new Tuple<Integer, Boolean>(eachNeighborHost.getAddress(), predictLable);
					path.add(thisHop);//ע??˳??
					/**????·????Ϣ**/
					
					/**ά????С????ʱ???Ķ???**/
					if (arrivalTime.containsKey(eachNeighborHost)){
						/**???????????Ƿ?????ͨ??????????·?????????У????ĸ?ʱ??????**/
						if (time <= arrivalTime.get(eachNeighborHost)){
							if (random.nextBoolean() == true && 
									time - arrivalTime.get(eachNeighborHost) < 0.1){//????ʱ?????ȣ?????????ѡ??
								
								/**ע?⣬?ڶԶ??н??е?????ʱ?򣬲??ܹ???forѭ???????Դ˶??н????޸Ĳ??????????ᱨ??**/
								int index = -1;
								for (Tuple<DTNHost, Double> t : PriorityQueue){
									if (t.getKey() == eachNeighborHost){
										index = PriorityQueue.indexOf(t);
									}
								}
								/**ע?⣬????????PriorityQueue???н??е?????ʱ?򣬲??ܹ???forѭ???????Դ˶??н????޸Ĳ??????????ᱨ??**/
								if (index > -1){
									PriorityQueue.remove(index);
									PriorityQueue.add(new Tuple<DTNHost, Double>(eachNeighborHost, time));
									arrivalTime.put(eachNeighborHost, time);
									routerTable.put(eachNeighborHost, path);
								}
							}
						}
						/**???????????Ƿ?????ͨ??????????·?????????У????ĸ?ʱ??????**/
					}
					else{						
						PriorityQueue.add(new Tuple<DTNHost, Double>(eachNeighborHost, time));
						arrivalTime.put(eachNeighborHost, time);
						routerTable.put(eachNeighborHost, path);
					}
					/**?Զ??н???????**/
					sort(PriorityQueue);					
					updateLabel = true;
				}
			}
			iteratorTimes++;
			for (int i = 0; i < PriorityQueue.size(); i++){
				if (!sourceSet.contains(PriorityQueue.get(i).getKey())){
					sourceSet.add(PriorityQueue.get(i).getKey());//???µ?????????????
					break;
				}
			}				
//			if (netgridRouterTable.containsKey(msg.getTo()))//??????;?ҵ???Ҫ??·??????ֱ???˳?????
//				break;
		}
		routerTableUpdateLabel = true;
	}
	
	/**
	 * ͨ????Ϣͷ???ڵ?·????Ϣ(?ڵ???ַ)?ҵ???Ӧ?Ľڵ㣬DTNHost??
	 * @param path
	 * @return
	 */
	public List<DTNHost> getHostListFromPath(List<Integer> path){
		List<DTNHost> hostsOfPath = new ArrayList<DTNHost>();
		for (int i = 0; i < path.size(); i++){
			hostsOfPath.add(this.getHostFromAddress(path.get(i)));//???ݽڵ???ַ?ҵ?DTNHost 
		}
		return hostsOfPath;
	}
	/**
	 * ͨ???ڵ???ַ?ҵ???Ӧ?Ľڵ㣬DTNHost??
	 * @param address
	 * @return
	 */
	public DTNHost getHostFromAddress(int address){
		for (DTNHost host : getHosts()){
			if (host.getAddress() == address)
				return host;
		}
		return null;
	}

	/**
	 * ????ͨ??Ԥ???ڵ㵽??????Ĵ???ʱ??(??????ʱ?????ϵȴ?ʱ??)
	 * @param msgSize
	 * @param startTime
	 * @param host
	 * @param nei
	 * @return
	 */
	public double calculatePredictionDelay(int msgSize, double startTime, DTNHost host, DTNHost nei){
		if (startTime >= SimClock.getTime()){
			double waitTime;
			waitTime = startTime - SimClock.getTime() + msgSize/((nei.getInterface(1).getTransmitSpeed() > 
									host.getInterface(1).getTransmitSpeed()) ? host.getInterface(1).getTransmitSpeed() : 
										nei.getInterface(1).getTransmitSpeed()) + this.transmitRange*1000/SPEEDOFLIGHT;//ȡ???߽?С?Ĵ???????;
			return waitTime;
		}
		else{
			assert false :"Ԥ??????ʧЧ ";
			return -1;
		}
	}
	/**
	 * ???㵱ǰ?ڵ???һ???ھӵĴ?????ʱ
	 * @param msgSize
	 * @param host
	 * @return
	 */
	public double calculateNeighborsDelay(int msgSize, DTNHost host){
		double transmitDelay = msgSize/((this.getHost().getInterface(1).getTransmitSpeed() > host.getInterface(1).getTransmitSpeed()) ? 
				host.getInterface(1).getTransmitSpeed() : this.getHost().getInterface(1).getTransmitSpeed()) + getDistance(this.getHost(), host)*1000/SPEEDOFLIGHT;//ȡ???߽?С?Ĵ???????
		return transmitDelay;
	}
	
	/**
	 * ?????????ڵ?֮???ľ???
	 * @param a
	 * @param b
	 * @return
	 */
	public double getDistance(DTNHost a, DTNHost b){
		double ax = a.getLocation().getX();
		double ay = a.getLocation().getY();
		double az = a.getLocation().getZ();
		double bx = a.getLocation().getX();
		double by = a.getLocation().getY();
		double bz = a.getLocation().getZ();
		
		double distance = (ax - bx)*(ax - bx) + (ay - by)*(ay - by) + (az - bz)*(az - bz);
		distance = Math.sqrt(distance);
		
		return distance;
	}
	/**
	 * Find the DTNHost according to its address
	 * @param address
	 * @return
	 */
	public Connection findConnection(int address){
		List<Connection> connections = this.getHost().getConnections();
		for (Connection c : connections){
			if (c.getOtherNode(this.getHost()).getAddress() == address){
				return c;
			}
		}
		return null;
	}
	/**
	 * ??????һ???Ŀ?ѡ?ڵ???ַ???ϣ?ѡ??һ???????ʵ???һ???ڵ㲢?ҵ???Ӧ??connection???з???
	 * @param address
	 * @return
	 */
	public Connection findConnectionFromHosts(Message msg, List<Integer> hostsInThisHop){
		if (hostsInThisHop.size() == 1){
			return findConnection(hostsInThisHop.get(0));
		}
		/**?ж?????ѡ??һ???ڵ???ʱ??**/
		else{
			/**ȷ??һ???Ĵ??䲻??????**/
			DTNHost destination = msg.getTo();
			for (int i = 0; i < hostsInThisHop.size(); i++){
				Connection connect = findConnection(hostsInThisHop.get(i));
				
				/**·???ҵ???·?????ܳ??ִ??󣬵??µ?ǰ·????????**/
				if (connect == null) 
					return null;
				/**·???ҵ???·?????ܳ??ִ??󣬵??µ?ǰ·????????**/
				
				if (connect.getOtherInterface(this.getHost().getInterface(1)).getHost() == destination)
					return connect;
			}
			/**ȷ??һ???Ĵ??䲻??????**/
			/****************************************************************!!!!!???޸?!!!!!!**************************************************************************/
			int randomInt = this.random.nextInt(hostsInThisHop.size());
			Connection con = findConnection(hostsInThisHop.get(randomInt) - 1);//ע??Ҫ??һ????Ϊ??ArrayList???????±?
			if (con != null){
				return con;
			}
			/**һ????һ??ʧ?ܾͽ??б???Ѱ??**/
			else{
				for (int i = 0; i < hostsInThisHop.size(); i++){
					con = findConnection(i);
					/**???????п????ԣ??ҳ?һ???ɴ????ھӽڵ㣬???򷵻?null**/
					if (con != null)
						return con;
				}
			}
			
			return null;
			/****************************************************************!!!!!???޸?!!!!!!**************************************************************************/
		}
	}
    /**
     * Try to send the message through a specific connection
     *
     * @param t
     * @return
     */
	public Message tryMessageToConnection(Tuple<Message, Connection> t){
		if (t == null)
			throw new SimError("No such tuple: " + 
					" at " + this);
		Message m = t.getKey();
		Connection con = t.getValue();
		int retVal = startTransfer(m, con);
		 if (retVal == RCV_OK) {  //accepted a message, don't try others
	            return m;     
	        } else if (retVal > 0) { //ϵͳ???壬ֻ??TRY_LATER_BUSY????0????Ϊ1
	            return null;          // should try later -> don't bother trying others
	        }
		 return null;
	}

    /**
     * Judge the next hop is busy or not.
     *
     * @param t
     * @return
     */
	public boolean nextHopIsBusyOrNot(Tuple<Message, Connection> t){		
		Connection con = t.getValue();
        if (con == null)
        	return false;
		/**??????????·????????????????һ??????·?Ѿ???ռ?ã?????Ҫ?ȴ?**/
		if (con.isTransferring() || ((NetGridRouter)con.getOtherNode(this.getHost()).getRouter()).isTransferring()){	
			return true;//˵??Ŀ?Ľڵ???æ
		}
		return false;
		/**???ڼ??????е???·ռ?????????????ڵ??Ƿ??ڶ??ⷢ?͵?????????update???????Ѿ????????ˣ??ڴ??????ظ?????**/
	}
    /**
     * Try to send the message through a specific connection.
     *
     * @param t
     * @return
     */
    public boolean sendMsg(Tuple<Message, Connection> t) {
        if (t == null) {
            assert false : "error!";
            return false;
        } else {
        	// check the next hop is busy or not
            if (nextHopIsBusyOrNot(t) == true)
                return false;
            if (tryMessageToConnection(t) != null)
                return true;
            else
                return false;
        }
    }
	/**
	 * Returns true if this router is transferring something at the moment or
	 * some transfer has not been finalized.
	 * @return true if this router is transferring something
	 */
	@Override
	public boolean isTransferring() {
		//?жϸýڵ??ܷ????д?????Ϣ??????????????һ?????ϵģ?ֱ?ӷ??أ???????,???????ŵ??ѱ?ռ?ã?
		//????1?????ڵ????????⴫??
		if (this.sendingConnections.size() > 0) {//protected ArrayList<Connection> sendingConnections;
			return true; // sending something
		}
		
		List<Connection> connections = getConnections();
		//????2??û???ھӽڵ?
		if (connections.size() == 0) {
			return false; // not connected
		}
		//????3?????ھӽڵ㣬??????????Χ?ڵ????ڴ???
		//ģ???????߹㲥??·?????ھӽڵ?֮??ͬʱֻ????һ?Խڵ㴫??????!!!!!!!!!!!!!!!!!!!!!!!!!!!
		//??Ҫ?޸?!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
		for (int i=0, n=connections.size(); i<n; i++) {
			Connection con = connections.get(i);
			if (!con.isReadyForTransfer()) {//isReadyForTransfer????false????ʾ???ŵ??ڱ?ռ?ã????˶??ڹ㲥?ŵ????Բ??ܴ???
				return true;	// a connection isn't ready for new transfer
			}
		}		
		return false;		
	}
	/**
	 * ????д??????֤?ڴ???????֮????Դ?ڵ?????Ϣ??messages??????ɾ??
	 */
	@Override
	protected void transferDone(Connection con){
		String msgId = con.getMessage().getId();
		if (msgId != null)
			removeFromMessages(msgId);
	}
    /**
     * get all satellite nodes info in the movement model
     *
     * @return all satellite nodes in the network
     */
    public List<DTNHost> getHosts() {
        return new ArrayList<DTNHost>(((SatelliteMovement) this.getHost().getMovementModel()).getHosts());
    }
	/**
	 * ????initInterSatelliteNeighbors()?????еı߽?ֵ????
	 * @param n
	 * @param upperBound
	 * @param lowerBound
	 * @return
	 */
	public int processBoundOfNumber(int n , int lowerBound, int upperBound){
		if (n < lowerBound){
			return n + upperBound + 1 + lowerBound;
		}
		if (n > upperBound){	
			return n - upperBound - 1 + lowerBound;
		}
		return n;
	}
	/**
	 * ??????ͬһ??ƽ???ڵĽڵ????ţ????ڱ߽?ʱ??????
	 * @param n
	 * @param nrofPlane
	 * @param nrofSatelliteInOnePlane
	 * @return
	 */
	public int processBound(int n ,int nrofPlane, int nrofSatelliteInOnePlane){
		int startNumber = nrofSatelliteInOnePlane * (nrofPlane - 1);//?˹???ƽ???ڵĽڵ㣬??ʼ????
		int endNumber = nrofSatelliteInOnePlane * nrofPlane - 1;//?˹???ƽ???ڵĽڵ㣬??β????
		if (n < startNumber)
			return endNumber;
		if (n > endNumber)
			return startNumber;
		//int nrofPlane = n/nrofSatelliteInOnePlane + 1;
		return n;
	}
	/**
	 * ??ʼ???趨???ڵ???ͬ???ھӽڵ?
	 */
	public void initInterSatelliteNeighbors(){
		Settings setting = new Settings("userSetting");
		Settings sat = new Settings("Group");
		int TOTAL_SATELLITES = sat.getInt("nrofHosts");//?ܽڵ???
		int TOTAL_PLANE = setting.getInt("nrofPlane");//?ܹ???ƽ????
		int NROF_S_EACHPLANE = TOTAL_SATELLITES/TOTAL_PLANE;//ÿ??????ƽ???ϵĽڵ???
		
		int thisHostAddress = this.getHost().getAddress();
		
		int upperBound = getHosts().size() - 1;
		int a = processBound(thisHostAddress + 1, thisHostAddress/NROF_S_EACHPLANE + 1, NROF_S_EACHPLANE);
		int b = processBound(thisHostAddress - 1, thisHostAddress/NROF_S_EACHPLANE + 1, NROF_S_EACHPLANE);		
		
		for (DTNHost host : getHosts()){
			if (host.getAddress() == a || host.getAddress() == b){
				neighborHostsInSamePlane.remove(host);
				neighborHostsInSamePlane.add(host);//ͬһ???????ڵ????ڽڵ?
			}
		}
	}
	/**
	 * ??ʼ???趨???ڵ??????ڹ??????ھӽڵ?(??Ϊ?ڱ?Ե????ƽ??ʱ?ļ򵥶?Ӧ??ϵ????һЩ???⣬??????Ҫ??̬????)
	 */
	public void updateInterSatelliteNeighbors(List<DTNHost> conNeighbors){	
		SatelliteMovement movementModel = ((SatelliteMovement)this.getHost().getMovementModel());
		
		int TOTAL_SATELLITES = movementModel.getTotalNrofLEOSatellites();//?ܽڵ???		
		int TOTAL_PLANE = movementModel.getTotalNrofLEOPlanes();//?ܹ???ƽ????
		int NROF_S_EACHPLANE = TOTAL_SATELLITES/TOTAL_PLANE;//ÿ??????ƽ???ϵĽڵ???
		
		int thisHostAddress = this.getHost().getAddress();
		
		int upperBound = getHosts().size() - 1;
		int c = processBoundOfNumber(thisHostAddress + NROF_S_EACHPLANE, 0, upperBound);
		int d = processBoundOfNumber(thisHostAddress - NROF_S_EACHPLANE, 0, upperBound);
		

		for (DTNHost host : getHosts()){
			if (host.getAddress() == c){
				if (!conNeighbors.contains(host)){
					double minDistance = Double.MAX_VALUE;
					DTNHost minHost = null;	
					for (DTNHost neighborHost : conNeighbors){//???????????ʹ????е?c?????Ĺ???ƽ???ϵĽڵ㣬ѡһ????????
						int planeOfC = c/NROF_S_EACHPLANE + 1;//???????ڽڵ??????????Ĺ???ƽ????
						if (planeOfC == movementModel.getNrofPlane()){
							if (neighborHost.getLocation().distance(this.getHost().getLocation()) < minDistance)
								minHost = neighborHost;
						}
					}
					if (minHost != null){
						neighborPlaneHosts.remove(minHost);//ȥ?ظ?????
						neighborPlaneHosts.add(minHost);
					}
				}
				else{
					neighborPlaneHosts.remove(host);//ȥ?ظ?????
					neighborPlaneHosts.add(host);
				}
			}
			
			if (host.getAddress() == d){
				if (!conNeighbors.contains(host)){
					double minDistance = Double.MAX_VALUE;
					DTNHost minHost = null;	
					for (DTNHost neighborHost : conNeighbors){
						int planeOfD = d/NROF_S_EACHPLANE + 1;//???????ڽڵ??????????Ĺ???ƽ????
						if (planeOfD == movementModel.getNrofPlane()){
							if (neighborHost.getLocation().distance(this.getHost().getLocation()) < minDistance)
								minHost = neighborHost;
						}
					}
					if (minHost != null){
						neighborPlaneHosts.remove(minHost);//ȥ?ظ?????
						neighborPlaneHosts.add(minHost);
					}
				}
				else{
					neighborPlaneHosts.remove(host);//ȥ?ظ?????
					neighborPlaneHosts.add(host);
				}
			}
		}
	}
	
	public class GridNeighbors {
		
		private List<DTNHost> hosts = new ArrayList<DTNHost>();//ȫ?????ǽڵ??б?
		private DTNHost host;
		private double transmitRange;
		private double msgTtl;
		
		private double updateInterval = 1;
		
		private GridCell[][][] cells;//GridCell?????࣬????һ??ʵ??????һ??????????????????world??????һ????ά?????洢??????????ÿ?????????ִ洢?˵?ǰ?????е?host??networkinterface
		
		private double cellSize;
		private int rows;
		private int cols;
		private int zs;//??????ά????
		private  int worldSizeX;
		private  int worldSizeY;
		private  int worldSizeZ;//????
		
		private int gridLayer;
		
		/**ÿ??routing???и???ʱ?????ڴ洢ָ??ʱ????????״̬???????ͽڵ???ӳ????ϵ**/
//		private HashMap<Double, HashMap<NetworkInterface, GridCell>> gridmap = new HashMap<Double, HashMap<NetworkInterface, GridCell>>();
//		private HashMap<Double, HashMap<GridCell, List<DTNHost>>> cellmap = new HashMap<Double, HashMap<GridCell, List<DTNHost>>>();
		
		/**??ǰ˲ʱʱ?̵?????״̬???????????ͽڵ???ӳ????ϵ**/
		HashMap<NetworkInterface, GridCell> interfaceToGridCell = new HashMap<NetworkInterface, GridCell>();
		HashMap<GridCell, List<DTNHost>> gridCellToHosts = new HashMap<GridCell, List<DTNHost>>();
		
		/*???ڳ?ʼ??ʱ???????????ڵ???һ???????ڵ?????????*/
		private HashMap <DTNHost, List<GridCell>> gridLocation = new HashMap<DTNHost, List<GridCell>>();//???Žڵ?????????????
		private HashMap <DTNHost, List<Double>> gridTime = new HashMap<DTNHost, List<Double>>();//???Žڵ㾭????Щ????ʱ??ʱ??
		private HashMap <DTNHost, Double> periodMap = new HashMap <DTNHost, Double>();//??¼?????ڵ???????????
		
		public GridNeighbors(DTNHost host){
			this.host = host;
			//System.out.println(this.host);
			Settings se = new Settings("Interface");
			transmitRange = se.getDouble("transmitRange");//???????ļ??ж?ȡ????????
			Settings set = new Settings("Group");
			msgTtl = set.getDouble("msgTtl");
			
			Settings s = new Settings(MovementModel.MOVEMENT_MODEL_NS);
			int [] worldSize = s.getCsvInts(MovementModel.WORLD_SIZE,3);//??????2ά?޸?Ϊ3ά
			worldSizeX = worldSize[0];
			worldSizeY = worldSize[1];
			worldSizeZ = worldSize[1];//??????ά???????????飡????????????????????????????????????????????????????????????????????????????????????????????
			
			Settings layer = new Settings("Group");
			this.gridLayer = layer.getInt("layer");
			
			switch(this.gridLayer){
			case 1 : 
				cellSize =  (transmitRange*0.2886751345);//Layer=2
				break;
			case 2 : 
				cellSize =  (transmitRange*0.144337567);//Layer=3
				break;
			case 3:
				cellSize =  (transmitRange*0.0721687836);//Layer=4
				break;
			default :
				cellSize =  (transmitRange*0.2886751345);//Layer=2
				break;
			}
			//cellSize = (int) (transmitRange*0.5773502);
			
			CreateGrid(cellSize);
			/*??ʼ????ǰ?????????ǹ?????Ϣ*/
			
		}
		public void setHost(DTNHost h){
			this.host = h;
		}
		public DTNHost getHost(){
			return this.host;
		}
		/**
		 * ??ʼ???????̶???????
		 * @param cellSize
		 */
		public void CreateGrid(double cellSize){
			this.rows = (int)Math.floor(worldSizeY/cellSize) + 1;
			this.cols = (int)Math.floor(worldSizeX/cellSize) + 1;
			this.zs = (int)Math.floor(worldSizeZ/cellSize) + 1;//????
			System.out.println(cellSize+"  "+this.rows+"  "+this.cols+"  "+this.zs);
			// leave empty cells on both sides to make neighbor search easier 
			this.cells = new GridCell[rows+2][cols+2][zs+2];
			this.cellSize = cellSize;

			for (int i=0; i<rows+2; i++) {
				for (int j=0; j<cols+2; j++) {
					for (int n=0;n<zs+2; n++){//??????ά????
						this.cells[i][j][n] = new GridCell();
						cells[i][j][n].setNumber(i, j, n);
					}
				}
			}
		}
		/**
		 * ???v???й??c????ÿ?????c???vһ???L?ڣ?ӛ????һ???L?ڃȱ??v?^?ľW?񣬲??ҵ????????M?????x?_?r?g
		 */
		public void initializeGridLocation(){	

			for (DTNHost h : getHosts()){//??ÿ?????c???vһ???L?ڣ?ӛ????һ???L?ڃȱ??v?^?ľW?񣬲??ҵ????????M?????x?_?r?g
				double period = getPeriodofOrbit(h);
				this.periodMap.put(h, period);
				System.out.println(this.host+" now calculate "+h+"  "+period);
				
				List<GridCell> gridList = new ArrayList<GridCell>();
				List<Double> intoTime = new ArrayList<Double>();
				List<Double> outTime = new ArrayList<Double>();
				GridCell startCell = cellFromCoord(h.getCoordinate(0));//??¼??ʼ????
				for (double time = 0; time < period; time += updateInterval){
					Coord c = h.getCoordinate(time);
					GridCell gc = cellFromCoord(c);//?????????ҵ??????ľW??
					if (!gridList.contains(gc)){
						if (gridList.isEmpty()){
							startCell = gc;//??¼??ʼ????
							gridList.add(null);//????ʼ??????һ?ηſ?ָ?룬ռ??λ
							intoTime.add(time);
						}						
						gridList.add(gc);//??һ?μ??⵽?ڵ?????????????ע?⣬?߽????飡??????ʼ?ͽ?????ʱ?򣡣???!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!??
						intoTime.add(time);//??¼??Ӧ?Ľ???ʱ??
						if (gc == startCell){
							gridList.set(0, startCell);
							intoTime.set(0, time);
						}
					}	
					else{
						//??????ʼ??????????????ʱ?䣬??һ????????????
						if (gc == startCell){
							gridList.set(0, startCell);
							intoTime.set(0, time);
						}						
					}
				}
				//System.out.println(h+" startCell "+h.getCoordinate(1)+" time: "+h.getCoordinate(0)+ "  "+h.getCoordinate(period)+ "  "+h.getCoordinate(6024)+ "  "+h.getCoordinate(6023));
				//System.out.println(h+" startCell "+startCell+" time: "+intoTime.get(0)+ "  "+intoTime.get(1)+"  "+intoTime.get(intoTime.size()-1)+"  "+gridLocation);
				gridLocation.put(h, gridList);//??????һ???ڵ??ͼ?¼????
				gridTime.put(h, intoTime);
			}
			System.out.println(gridLocation);
		}
		
		
		/**???ڲ??????˼????Ŀ???????ɾ**/
		public void computationComplexityOfGridCalculation(double time, int RunningTimes){	
			
			double orbitCost = 0;
			double GridCost = 0;
			double totalCost = 0;
			
			HashMap<DTNHost, Tuple<Coord, GridCell>> relationship = new HashMap<DTNHost, Tuple<Coord, GridCell>>();
			HashMap<GridCell, DTNHost> gridMap = new HashMap<GridCell, DTNHost>();
			HashMap<GridCell, List<GridCell>> edges = new HashMap<GridCell, List<GridCell>>();
			
			
			
			for (int n = 0; n < RunningTimes; n++){
				double t00 = System.nanoTime();//???ӶȲ??Դ???,??ȷ??????
				for (DTNHost h : getHosts()){//??ÿ?????c???vһ???L?ڣ?ӛ????һ???L?ڃȱ??v?^?ľW?񣬲??ҵ????????M?????x?_?r?g
						
					Coord c = h.getCoordinate(time);					
					
					GridCell gc = cellFromCoord(c);//?????????ҵ??????ľW??
					relationship.put(h, new Tuple<Coord, GridCell>(c, gc));
					
					gridMap.put(gc, h);
					
				}	
				double t01 = System.nanoTime();//???ӶȲ??Դ???,??ȷ??????
				System.out.println(n+"  ???????㿪??: "+ (t01-t00));
				orbitCost += (t01-t00);
				
				
				for (DTNHost h : getHosts()){
					GridCell gc = relationship.get(h).getValue();
					List<GridCell> neighbors = new ArrayList<GridCell>();
				//	for (int i = 0; i < 132; i++){
						for (GridCell c : getLayer1(gc.number[0], gc.number[1], gc.number[2])){
							if (gridMap.containsKey(c)){
								neighbors.add(c);
							}
					
						}
						edges.put(gc, neighbors);
					}

				//}
				double t02 = System.nanoTime();//???ӶȲ??Դ???,??ȷ??????
				System.out.println(n+"  ?ܹ?????: " + (t02-t00) + "  ?????????Լ????ſ???: "+ (t02-t01));
				GridCost += (t02-t01);
				totalCost += (t02-t00);
			}		
			System.out.println("  ƽ???ܹ?????: " + totalCost/RunningTimes + "  ƽ?????????㿪??: "+ orbitCost/RunningTimes + "  ƽ???߼??㿪??: "+ GridCost/RunningTimes);
			throw new SimError("Pause");		
		}
		/**???ڲ??????˼????Ŀ???????ɾ**/
		public GridCell[] getLayer1(int row, int col, int z){
			List<GridCell> GC = new ArrayList<GridCell>();
			return new GridCell[] {
					cells[row-1][col-1][z],cells[row-1][col][z],cells[row-1][col+1][z],//1st row
					cells[row][col-1][z],cells[row][col][z],cells[row][col+1][z],//2nd row
					cells[row+1][col-1][z],cells[row+1][col][z],cells[row+1][col+1][z],//3rd row
							
					cells[row-1][col-1][z],cells[row-1][col][z],cells[row-1][col+1][z],//1st row
					cells[row][col-1][z],cells[row][col][z],cells[row][col+1][z],//2nd row
					cells[row+1][col-1][z],cells[row+1][col][z],cells[row+1][col+1][z],//3rd row	
							
					cells[row-1][col-1][z],cells[row-1][col][z],cells[row-1][col+1][z],//1st row
					cells[row][col-1][z],cells[row][col][z],cells[row][col+1][z],//2nd row
					cells[row+1][col-1][z],cells[row+1][col][z],cells[row+1][col+1][z]//3rd row
				};
//			for (int i = -1; i < 2; i += 1){
//				for (int j = -1; j < 2; j += 1){
//					for (int k = -1; k < 2; k += 1){
//						GC.add(cells[row+i][col+j][z+k]);
//					}
//				}
//			}
//			return GC;
		}
		/**???ڲ??????˼????Ŀ???????ɾ**/

		
		
		/**
		 * ?@ȡָ???l?ǹ??c???\???L?ڕr?g
		 * @param h
		 * @return
		 */
		public double getPeriodofOrbit(DTNHost h){
			return ((SatelliteMovement)this.getHost().getMovementModel()).getPeriod();
		}
		
		/**
		 * ?ҵ?host?ڵ??ڵ?ǰʱ????Ӧ???ڵ?????
		 * @param host
		 * @param time
		 * @return
		 */
		public GridCell getGridCellFromCoordNow(DTNHost host){
			/**ע????????ʽ???õ????????꣬??ʵʱ??ά?????????õ???????????֮?????????????**/
			return this.interfaceToGridCell.get(host.getInterface(1));
			//return cellFromCoord(host.getCoordinate(time));
		}
		
		/**
		 * ?ҵ?host?ڵ???ʱ??time??Ӧ???ڵ?????
		 * @param host
		 * @param time
		 * @return
		 */
		public GridCell getGridCellFromCoordAtTime(DTNHost host, double time){
			/**ע????????ʽ???õ????????꣬??ʵʱ??ά?????????õ???????????֮?????????????**/
			return this.interfaceToGridCell.get(host.getInterface(1));
			//return cellFromCoord(host.getCoordinate(time));
		}
		/**
		 * ??ȡָ??ʱ???㣬ָ?????????ھ?????
		 * @param source
		 * @param time
		 * @return
		 */
		public HashMap<GridCell, Tuple<GridCell, List<DTNHost>>> getNeighborsNetgridsNow(GridCell source){//??ȡָ??ʱ?????ھӽڵ?(ͬʱ????Ԥ?⵽TTLʱ???ڵ??ھ?)	

			//HashMap<NetworkInterface, GridCell> ginterfaces = gridmap.get(time);
			//GridCell cell = this.interfaceToGridCell.get(host.getInterface(1));
			int[] number = source.getNumber();//?õ???????????ά????
			
			List<GridCell> cellList = getNeighborCells(number[0], number[1], number[2]);//?????ھӵ????񣨵?ǰʱ?̣?
			/**?ҳ????е??ھ??????Լ????????Ľڵ?**/
			HashMap<GridCell, Tuple<GridCell, List<DTNHost>>> neighborNetgridInfo = new HashMap<GridCell, Tuple<GridCell, List<DTNHost>>>();
			//List<Tuple<GridCell, List<DTNHost>>> gridInfoList = new ArrayList<Tuple<GridCell, List<DTNHost>>>();
			/**?ҳ????е??ھ??????Լ????????Ľڵ?**/
			//assert cellmap.containsKey(time):" ʱ?????? ";
			/**ȥ????????**/
			if (cellList.contains(source))
				cellList.remove(source);
			
			for (GridCell c : cellList){
				if (this.gridCellToHosts.containsKey(c)){//??????????????˵?????ھ?????Ϊ?գ????治???κνڵ?
					List<DTNHost> hostList = new ArrayList<DTNHost>(this.gridCellToHosts.get(c));//?ҳ???һ???ھ??????ڶ?Ӧ?????нڵ?
					Tuple<GridCell, List<DTNHost>> oneNeighborGrid = new Tuple<GridCell, List<DTNHost>>(c, hostList);
					neighborNetgridInfo.put(c, oneNeighborGrid);
				}
			}	
			
			//System.out.println(host+" ?ھ??б?   "+hostList);
			return neighborNetgridInfo;
		}
		/**
		 * ??ȡ??ǰ????ʱ???£?ָ?????????ھ????????????е??????ھӽڵ?
		 * @param source
		 * @param time
		 * @return
		 */
		public List<DTNHost> getNeighborsHostsNow(GridCell source){//??ȡָ??ʱ?????ھӽڵ?(ͬʱ????Ԥ?⵽TTLʱ???ڵ??ھ?)	
			//HashMap<NetworkInterface, GridCell> ginterfaces = gridmap.get(time);
			//GridCell cell = this.interfaceToGridCell.get(host.getInterface(1));
			int[] number = source.getNumber();//?õ???????????ά????
			
			List<GridCell> cellList = getNeighborCells(number[0], number[1], number[2]);//?????ھӵ????񣨵?ǰʱ?̣?
			/**?ҳ????е??ھ??????Լ????????Ľڵ?**/

			/**ȥ????????**/
			if (cellList.contains(source))
				cellList.remove(source);
			
			List<DTNHost> neighborHosts = new ArrayList<DTNHost>();
			
			for (DTNHost host : hosts){
				GridCell cell = cellFromCoord(host.getLocation());
				if (cellList.contains(cell))
					neighborHosts.add(host);
				
			}
//			for (GridCell c : cellList){
//				if (this.gridCellToHosts.containsKey(c)){//??????????????˵?????ھ?????Ϊ?գ????治???κνڵ?
//					neighborHosts.addAll(this.gridCellToHosts.get(c));//?ҳ???һ???ھ??????ڶ?Ӧ?????нڵ?
//				}
//			}	
			
			//System.out.println(host+" ?ھ??б?   "+hostList);
			return neighborHosts;
		}
		
//		public List<DTNHost> getNeighbors(DTNHost host, double time){//??ȡָ??ʱ?????ھӽڵ?(ͬʱ????Ԥ?⵽TTLʱ???ڵ??ھ?)
//			int num = (int)((time-SimClock.getTime())/updateInterval);
//			time = SimClock.getTime()+num*updateInterval;
//			
//			if (time > SimClock.getTime()+msgTtl*60){//??????????ʱ???Ƿ񳬹?Ԥ??ʱ??
//				//assert false :"????Ԥ??ʱ??";
//				time = SimClock.getTime()+msgTtl*60;
//			}
//			
//			//double t0 = System.currentTimeMillis();
//			//System.out.println(t0);
//			
//			HashMap<NetworkInterface, GridCell> ginterfaces = gridmap.get(time);
//			GridCell cell = ginterfaces.get(host.getInterface(1));
//			int[] number = cell.getNumber();
//			
//			List<GridCell> cellList = getNeighborCells(time, number[0], number[1], number[2]);//?????ھӵ????񣨵?ǰʱ?̣?
//			List<DTNHost> hostList = new ArrayList<DTNHost>();//(?ھ??????ڵĽڵ㼯??)
//			assert cellmap.containsKey(time):" ʱ?????? ";
//			for (GridCell c : cellList){
//				if (cellmap.get(time).containsKey(c))//??????????????˵?????ھ?????Ϊ?գ????治???κνڵ?
//					hostList.addAll(cellmap.get(time).get(c));
//			}	
//			if (hostList.contains(host))//???????ڵ?ȥ??
//				hostList.remove(host);
//			
//			//double t1 = System.currentTimeMillis();
//			//System.out.println("search cost"+(t1-t0));
//			//System.out.println(host+" ?ھ??б?   "+hostList);
//			return hostList;
//		}

		
//		public Tuple<HashMap<DTNHost, List<Double>>, //neiList Ϊ?Ѿ????????ĵ?ǰ?ھӽڵ??б?
//			HashMap<DTNHost, List<Double>>> getFutureNeighbors(List<DTNHost> neiList, DTNHost host, double time){
//			int num = (int)((time-SimClock.getTime())/updateInterval);
//			time = SimClock.getTime()+num*updateInterval;	
//			
//			HashMap<DTNHost, List<Double>> leaveTime = new HashMap<DTNHost, List<Double>>();
//			HashMap<DTNHost, List<Double>> startTime = new HashMap<DTNHost, List<Double>>();
//			for (DTNHost neiHost : neiList){
//				List<Double> t= new ArrayList<Double>();
//				t.add(SimClock.getTime());
//				startTime.put(neiHost, t);//?????Ѵ????ھӽڵ??Ŀ?ʼʱ??
//			}
//			
//			List<DTNHost> futureList = new ArrayList<DTNHost>();//(?ھ??????ڵ?δ???ڵ㼯??)
//			List<NetworkInterface> futureNeiList = new ArrayList<NetworkInterface>();//(Ԥ??δ???ھӵĽڵ㼯??)
//			
//			
//			Collection<DTNHost> temporalNeighborsBefore = startTime.keySet();//ǰһʱ?̵??ھӣ?ͨ???????Ա???һʱ?̵??ھӣ???֪????Щ???¼????ģ???Щ?????뿪??			
//			Collection<DTNHost> temporalNeighborsNow = new ArrayList<DTNHost>();//???ڼ?¼??ǰʱ?̵??ھ?
//			for (; time < SimClock.getTime() + msgTtl*60; time += updateInterval){
//				
//				HashMap<NetworkInterface, GridCell> ginterfaces = gridmap.get(time);//ȡ??timeʱ?̵???????
//				GridCell cell = ginterfaces.get(host.getInterface(1));//?ҵ???ʱָ???ڵ???????????λ??
//				
//				int[] number = cell.getNumber();
//				List<GridCell> cellList = getNeighborCells(time, number[0], number[1], number[2]);//??ȡ?????ھӵ????񣨵?ǰʱ?̣?
//				
//				for (GridCell c : cellList){	//?????ڲ?ͬʱ??ά???ϣ?ָ???ڵ???Χ???????ھ?
//					if (!cellmap.get(time).containsKey(c))
//						continue;
//					temporalNeighborsNow.addAll(cellmap.get(time).get(c));
//					for (DTNHost ni : cellmap.get(time).get(c)){//???鵱ǰԤ??ʱ???㣬???е??ھӽڵ?
//						if (ni == this.host)//?ų??????ڵ?
//							continue;
//						if (!neiList.contains(ni))//?????????ھ???û?У???һ????δ???????????ھ?					
//							futureList.add(ni); //??Ϊδ?????ᵽ?????ھ?(??Ȼ???ڵ?ǰ???е??ھӣ?Ҳ???ܻ???;?뿪??Ȼ???ٻ???)
//										
//						/**??????δ?????????ھӣ?ֱ??get?᷵?ؿ?ָ?룬????Ҫ?ȼ?startTime??leaveTime?ж?**/
//						if (startTime.containsKey(ni)){
//							if (leaveTime.isEmpty())
//								break;
//							if (startTime.get(ni).size() == leaveTime.get(ni).size()){//????????????һ?????ھӽڵ??뿪??????					
//								List<Double> mutipleTime= leaveTime.get(ni);
//								mutipleTime.add(time);
//								startTime.put(ni, mutipleTime);//?????µĿ?ʼʱ??????
//							}
//							/*if (leaveTime.containsKey(ni)){//????????????һ????Ԥ??ʱ?????ڴ??ھӻ??뿪????һ???????Ǵ??ھӲ????ڴ?ʱ?????ڻ??뿪????????
//								if (startTime.get(ni).size() == leaveTime.get(ni).size()){//????????????һ?????ھӽڵ??뿪??????					
//									List<Double> mutipleTime= leaveTime.get(ni);
//									mutipleTime.add(time);
//									startTime.put(ni, mutipleTime);//?????µĿ?ʼʱ??????
//								}
//								else{
//									List<Double> mutipleTime= leaveTime.get(ni);
//									mutipleTime.add(time);
//									leaveTime.put(ni, mutipleTime);//?????µ??뿪ʱ??????
//								}	
//							}
//							else{
//								List<Double> mutipleTime= new ArrayList<Double>();
//								mutipleTime.add(time);
//								leaveTime.put(ni, mutipleTime);//?????µ??뿪ʱ??????
//							}*/
//						}
//						else{
//							//System.out.println(this.host+" ????Ԥ???ڵ?: "+ni+" ʱ??  "+time);
//							List<Double> mutipleTime= new ArrayList<Double>();
//							mutipleTime.add(time);
//							startTime.put(ni, mutipleTime);//?????µĿ?ʼʱ??????
//						}
//						/**??????δ?????????ھӣ?ֱ??get?᷵?ؿ?ָ?룬????Ҫ?ȼ?startTime??leaveTime?ж?**/
//					}	
//				}
//				
//				for (DTNHost h : temporalNeighborsBefore){//?????Ա???һʱ?̺???һʱ?̵??ھӽڵ㣬?Ӷ??ҳ??뿪???ھӽڵ?
//					if (!temporalNeighborsNow.contains(h)){
//						List<Double> mutipleTime= leaveTime.get(h);
//						mutipleTime.add(time);
//						leaveTime.put(h, mutipleTime);//?????µ??뿪ʱ??????
//					}						
//				}
//				temporalNeighborsBefore.clear();
//				temporalNeighborsBefore = temporalNeighborsNow;
//				temporalNeighborsNow.clear();	
//			}
//			
//			Tuple<HashMap<DTNHost, List<Double>>, HashMap<DTNHost, List<Double>>> predictTime= //??Ԫ???ϲ???ʼ?ͽ???ʱ??
//					new Tuple<HashMap<DTNHost, List<Double>>, HashMap<DTNHost, List<Double>>>(startTime, leaveTime); 
//			
//			
//			return predictTime;
//		}


		public List<GridCell> getNeighborCells(int row, int col, int z){
			//HashMap<GridCell, List<DTNHost>> cellToHost = this.gridCellToHosts;//??ȡtimeʱ?̵?ȫ????????
			List<GridCell> GC = new ArrayList<GridCell>();
			/***********************************************************************/
			switch(this.gridLayer){
			case 1 : //ֻռ??%15.5??????
			/*?????????ָ?*/
				for (int i = -1; i < 2; i += 1){
					for (int j = -1; j < 2; j += 1){
						for (int k = -1; k < 2; k += 1){
							GC.add(cells[row+i][col+j][z+k]);
						}
					}
				}
				break;
			case 2 : {//m=1ʱ??ֻռ??%28.5??????
			/*?????????ָ?*/
				for (int i = -3; i <= 3; i += 1){
					for (int j = -3; j <= 3; j += 1){
						for (int k = -3; k <= 3; k += 1){
							if (boundaryCheck(row+i,col+j,z+k))
								GC.add(cells[row+i][col+j][z+k]);
						}
					}
				}
				int m = 1;//Ĭ??m = 1;
				for (int j = -m; j <= m; j += 1){
					for (int k = -m; k <= m; k += 1){
						if (boundaryCheck(row+4,col+j,z+k)){
							GC.add(cells[row+4][col+j][z+k]);
						}
					}
				}
				for (int j = -m; j <= m; j += 1){
					for (int k = -m; k <= m; k += 1){
						if (boundaryCheck(row-4,col+j,z+k))
							GC.add(cells[row-4][col+j][z+k]);
					}
				}
				for (int j = -m; j <= m; j += 1){
					for (int k = -m; k <= m; k += 1){
						if (boundaryCheck(row+j,col+4,z+k))
							GC.add(cells[row+j][col+4][z+k]);
					}
				}
				for (int j = -m; j <= m; j += 1){
					for (int k = -m; k <= m; k += 1){
						if (boundaryCheck(row+j,col-4,z+k))
							GC.add(cells[row+j][col-4][z+k]);
					}
				}
				for (int j = -m; j <= m; j += 1){
					for (int k = -m; k <= m; k += 1){
						if (boundaryCheck(row+j,col+k,z+4))
							GC.add(cells[row+j][col+k][z+4]);
					}
				}
				for (int j = -m; j <= m; j += 1){
					for (int k = -m; k <= m; k += 1){
						if (boundaryCheck(row+j,col+k,z-4))
							GC.add(cells[row+j][col+k][z-4]);
					}
				}	
			}
			break;
			default :/*?????????ָ?*/
				for (int i = -1; i < 2; i += 1){
					for (int j = -1; j < 2; j += 1){
						for (int k = -1; k < 2; k += 1){
							GC.add(cells[row+i][col+j][z+k]);	
						}
					}
				}
				break;
			
			case 3:{//n1=4,n2=2ʱ??ֻռ??%36??????
				/*?Ĳ????????ָ?*/
				for (int i = -7; i <= 7; i += 1){
					for (int j = -7; j <= 7; j += 1){
						for (int k = -7; k <= 7; k += 1){
							if (boundaryCheck(row+i,col+j,z+k))
								GC.add(cells[row+i][col+j][z+k]);

						}
					}
				}
				int n1 = 2;//Ĭ??n1 = 2;
				for (int j = -n1; j <= n1; j += 1){
					for (int k = -n1; k <= n1; k += 1){
						if (boundaryCheck(row+8,col+j,z+k)){
							GC.add(cells[row+8][col+j][z+k]);

						}
					}
				}
				for (int j = -n1; j <= n1; j += 1){
					for (int k = -n1; k <= n1; k += 1){
						if (boundaryCheck(row-8,col+j,z+k))
							GC.add(cells[row-8][col+j][z+k]);

					}
				}
				for (int j = -n1; j <= n1; j += 1){
					for (int k = -n1; k <= n1; k += 1){
						if (boundaryCheck(row+j,col+8,z+k))
							GC.add(cells[row+j][col+8][z+k]);

					}
				}
				for (int j = -n1; j <= n1; j += 1){
					for (int k = -n1; k <= n1; k += 1){
						if (boundaryCheck(row+j,col-8,z+k))
							GC.add(cells[row+j][col-8][z+k]);

					}
				}
				for (int j = -n1; j <= n1; j += 1){
					for (int k = -n1; k <= n1; k += 1){
						if (boundaryCheck(row+j,col+k,z+8))
							GC.add(cells[row+j][col+k][z+8]);

					}
				}
				for (int j = -n1; j <= n1; j += 1){
					for (int k = -n1; k <= n1; k += 1){
						if (boundaryCheck(row+j,col+k,z-8))
							GC.add(cells[row+j][col+k][z-8]);

					}
				}
				//
				int n2 = 1;//Ĭ??n2 = 1;
				for (int j = -n2; j <= n2; j += 1){
					for (int k = -n2; k <= n2; k += 1){
						if (boundaryCheck(row+9,col+j,z+k)){
							GC.add(cells[row+9][col+j][z+k]);

						}
					}
				}
				for (int j = -n2; j <= n2; j += 1){
					for (int k = -n2; k <= n2; k += 1){
						if (boundaryCheck(row-9,col+j,z+k))
							GC.add(cells[row-9][col+j][z+k]);

					}
				}
				for (int j = -n2; j <= n2; j += 1){
					for (int k = -n2; k <= n2; k += 1){
						if (boundaryCheck(row+j,col+9,z+k))
							GC.add(cells[row+j][col+9][z+k]);

					}
				}
				for (int j = -n2; j <= n2; j += 1){
					for (int k = -n2; k <= n2; k += 1){
						if (boundaryCheck(row+j,col-9,z+k))
							GC.add(cells[row+j][col-9][z+k]);

					}
				}
				for (int j = -n2; j <= n2; j += 1){
					for (int k = -n2; k <= n2; k += 1){
						if (boundaryCheck(row+j,col+k,z+9))
							GC.add(cells[row+j][col+k][z+9]);

					}
				}
				for (int j = -n2; j <= n2; j += 1){
					for (int k = -n2; k <= n2; k += 1){
						if (boundaryCheck(row+j,col+k,z-9))
							GC.add(cells[row+j][col+k][z-9]);

					}
				}
			}
		}
			//GC.add(cells[row][col][z]);//?޸??ھ?????????????????????????????????????????????????????????????????????????????????????????????????
			/***********************************************************************/
			return GC;
		}
		
		public boolean boundaryCheck(int i, int j, int k){
			if (i<0 || j<0 || k<0)
				return false;
			if (i > rows+1 || j > cols+1 || k > zs+1){
				return false;
			}
			return true;
		}
		
		public boolean isHostsListEmpty(){
			return this.hosts.isEmpty();
		}
		
		/**
		 * ?????µ???????ȡ??ʽ?£?·???㷨??????
		 * @param simClock
		 */
		public void updateNetGridInfo_without_OrbitCalculation_without_gridTable(){
			//if (gridLocation.isEmpty())//??ʼ??ִֻ??һ??
			//	initializeGridLocation();
			
			HashMap<NetworkInterface, GridCell> ginterfaces = new HashMap<NetworkInterface, GridCell>();//ÿ??????;			
			HashMap<GridCell, List<DTNHost>> cellToHost = new HashMap<GridCell, List<DTNHost>>();
			
			for (DTNHost host : hosts){
				GridCell cell = null;
			
				cell = cellFromCoord(host.getLocation());
				//cell = this.getGridCellFromCoordNow(host);
				if (cell == null)
					throw new SimError(" cell error!");
				
				ginterfaces.put(host.getInterface(1), cell);
				
				List<DTNHost> hostList = new ArrayList<DTNHost>();
				if (cellToHost.containsKey(cell)){
					hostList.addAll(cellToHost.get(cell));	
				}
				hostList.add(host);
				cellToHost.put(cell, hostList);
			}		
			gridCellToHosts.clear();
			interfaceToGridCell.clear();
			
			gridCellToHosts.putAll(cellToHost);
			interfaceToGridCell.putAll(ginterfaces);				
		}
		
		/**
		 * ??ǰ?????˸?????????һ???????ڵ??????????????????ɹ?????Ӧ?????????????????ݴ˱??Ϳ??Լ????໥֮??δ???Ĺ?ϵ?????????ټ???????
		 */
		public void updateNetGridInfo_without_OrbitCalculation(double simClock){
			if (gridLocation.isEmpty())//??ʼ??ִֻ??һ??
				initializeGridLocation();
			
			HashMap<NetworkInterface, GridCell> ginterfaces = new HashMap<NetworkInterface, GridCell>();//ÿ??????;
			//ginterfaces.clear();//ÿ??????
			//Coord location = new Coord(0,0); 	// where is the host
			//double simClock = SimClock.getTime();
			//System.out.println("update time:  "+ simClock);
				
			//int[] coordOfNetgrid;
			
			HashMap<GridCell, List<DTNHost>> cellToHost = new HashMap<GridCell, List<DTNHost>>();
			for (DTNHost host : hosts){
				/**??¼?????ڵ?????????????**/
				List<GridCell> gridCellList = this.gridLocation.get(host);
				/**??¼?????ڵ???????????ʱ??Ӧ?Ľ???ʱ??**/
				List<Double> timeList = this.gridTime.get(host);

				if (gridCellList.size() != timeList.size()){
					throw new SimError("????Ԥ???õ????????????⣡");	
				}
				/**???ǹ???????**/
				double period = this.periodMap.get(host);
				double t0 = simClock;
				GridCell cell = null;
				boolean label = false;
					
				if (simClock > period)
					t0 = t0 % period;//???????ھ?ȡ??????
				
				if (timeList.get(0) > timeList.get(timeList.size() - 1)){
					for (int iterator = 1; iterator < timeList.size(); iterator++){
						if (timeList.get(iterator) > t0){
							/**ע?⣬????iterator - 1??û?д??ģ???Ϊ????iterator????˵??????һ????????????ʱ?䣬????if???????㣬??ô??ʱ?̽ڵ?Ӧ?ô???ǰһ??????λ?õ???**/
							int[] coordOfNetgrid = gridCellList.get(iterator - 1).getNumber();
							cell = this.cells[coordOfNetgrid[0]][coordOfNetgrid[1]][coordOfNetgrid[2]];
							//cell = gridCellList.get(iterator - 1);
							label = true;
							break;
						}
					}
					/**?ж??ǲ??Ǵ??ڹ??????ڵ?ĩβʱ?̣??߽?λ??**/
					if (t0 >= timeList.get(0) & cell == null){
						int[] coordOfNetgrid = gridCellList.get(0).getNumber();
						cell = this.cells[coordOfNetgrid[0]][coordOfNetgrid[1]][coordOfNetgrid[2]];
						label = true;
					}
					
					if (t0 >= timeList.get(timeList.size() - 1) & t0 < timeList.get(0) & cell == null){
						int[] coordOfNetgrid = gridCellList.get(timeList.size() - 1).getNumber();
						cell = this.cells[coordOfNetgrid[0]][coordOfNetgrid[1]][coordOfNetgrid[2]];
						label = true;
					}	
				}
				else{
					for (int iterator = 1; iterator < timeList.size(); iterator++){
						if (timeList.get(iterator) > t0){
							/**ע?⣬????iterator - 1??û?д??ģ???Ϊ????iterator????˵??????һ????????????ʱ?䣬????if???????㣬??ô??ʱ?̽ڵ?Ӧ?ô???ǰһ??????λ?õ???**/
							int[] coordOfNetgrid = gridCellList.get(iterator - 1).getNumber();
							cell = this.cells[coordOfNetgrid[0]][coordOfNetgrid[1]][coordOfNetgrid[2]];
							//cell = gridCellList.get(iterator - 1);
							label = true;
							break;
						}
					}
					/**?ж??ǲ??Ǵ??ڹ??????ڵ?ĩβʱ?̣??߽?λ??**/
					if (t0 >= timeList.get(timeList.size() - 1) & cell == null){
						int[] coordOfNetgrid = gridCellList.get(timeList.size() - 1).getNumber();
						cell = this.cells[coordOfNetgrid[0]][coordOfNetgrid[1]][coordOfNetgrid[2]];
						//cell = gridCellList.get(0);
						label = true;
					}	
				}
			
//				for (double t : timeList){
//					if (t >= t0){
//						cell = gridCellList.get(iterator);
//						label = true;
//						break;
//					}
//					iterator++;//?ҵ???timeListʱ????Ӧ??????????λ??,iterator ????????????list?е?ָ??						
//				}				
				//System.out.println(host+"  "+cell+" time "+SimClock.getTime());

				if (label != true){
					/**????ǰ??û???ҵ?????˵????ʱ?ڵ?????һ???????????ڵģ?????????һ???????͵?һ???????Ľ??紦,Ӧ??ȡ????һ??????**/
//					int[] coordOfNetgrid = gridCellList.get(timeList.size() - 1).getNumber();
//					cell = this.cells[coordOfNetgrid[0]][coordOfNetgrid[1]][coordOfNetgrid[2]];
					System.out.println(simClock+"  "+host);
					throw new SimError("grid calculation error");
				}
				
//				/**??֤??**/
//				int[] coordOfNetgrid = cell.getNumber();
//				int[] TRUEcoordOfNetgrid = this.getGridCellFromCoordAtTime(host, simClock).getNumber();
//				if (!(TRUEcoordOfNetgrid[0] == coordOfNetgrid[0] & TRUEcoordOfNetgrid[0] == coordOfNetgrid[0] & TRUEcoordOfNetgrid[0] == coordOfNetgrid[0])){
//					System.out.println(simClock+"  "+host+" coordofnetgrid "+TRUEcoordOfNetgrid[0]+" "+ TRUEcoordOfNetgrid[1]+" "+TRUEcoordOfNetgrid[2]+"  "+coordOfNetgrid[0]+" "+coordOfNetgrid[1]+" "+coordOfNetgrid[2]);
//					//cell = this.getGridCellFromCoord(host, simClock);
//					//throw new SimError("grid calculation error");	
//				}					
//				/**??֤??**/

				ginterfaces.put(host.getInterface(1), cell);
				
				List<DTNHost> hostList = new ArrayList<DTNHost>();
				if (cellToHost.containsKey(cell)){
					hostList = cellToHost.get(cell);	
				}
				hostList.add(host);
				cellToHost.put(cell, hostList);
			}		
			gridCellToHosts.clear();
			interfaceToGridCell.clear();
			
			gridCellToHosts.putAll(cellToHost);
			interfaceToGridCell.putAll(ginterfaces);
			//ginterfaces = new HashMap<NetworkInterface, GridCell>();//ÿ??????
			
			
//			cellmap.put(simClock, cellToHost);
//			gridmap.put(simClock, ginterfaces);//Ԥ??δ??timeʱ?????ڵ???????֮???Ķ?Ӧ??ϵ
			//ginterfaces.clear();//ÿ??????
			
			//CreateGrid(cellSize);//????cells??new??ginterfaces??new
				
		}		
	
		/**
		 * ????????c?ҵ?c??????????
		 * @param c
		 * @return
		 */
		private GridCell cellFromCoord(Coord c) {
			// +1 due empty cells on both sides of the matrix
			int row = (int)Math.ceil(c.getY()/cellSize); 
			int col = (int)Math.ceil(c.getX()/cellSize);
			int z = (int)Math.ceil(c.getZ()/cellSize);
			if (!(row > 0 && row <= rows && col > 0 && col <= cols))
				throw new SimError("Location " + c + " is out of world's bounds");
			//assert row > 0 && row <= rows && col > 0 && col <= cols : "Location " + 
			//c + " is out of world's bounds";
		
			return this.cells[row][col][z];
		}
		
		public void setHostsList(List<DTNHost> hosts){
			this.hosts = hosts;
		}
		
		/**
		 * ?½??ڲ??࣬????ʵ?????񻮷֣??洢????????????ɢ????
		 */
		public class GridCell {
			// how large array is initially chosen
			private static final int EXPECTED_INTERFACE_COUNT = 18;
			//private ArrayList<NetworkInterface> interfaces;//GridCell????????ά???????ӿ??б???????¼?ڴ??????ڵĽڵ㣬????ȫ????????˵????Ҫ??֤ͬһ???????ӿڲ???ͬʱ??????????GridCell??
			private int[] number;
			
			private GridCell() {
			//	this.interfaces = new ArrayList<NetworkInterface>(
			//			EXPECTED_INTERFACE_COUNT);
				number = new int[3];
			}
			
			public void setNumber(int row, int col, int z){
				number[0] = row;
				number[1] = col;
				number[2] = z;
			}
			public int[] getNumber(){
				return number;
			}
			
			public String toString() {
				return getClass().getSimpleName() + " with " + 
					"cell number: "+ number[0]+" "+number[1]+" "+number[2];
			}
		}
	}
}
