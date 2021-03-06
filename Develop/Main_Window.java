/* 
 * Copyright 2016 University of Science and Technology of China , Infonet
 * 
 */
package Develop;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import chartAnalysisWindow.src.chartWindow.AddChartFrame;
import chartAnalysisWindow.src.chartWindow.AnalysisWindow;
import chartAnalysisWindow.src.chartWindow.Loadtxt;
import core.DTNHost;
import core.SimClock;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main_Window extends JFrame implements ActionListener, ChangeListener{
	private static final String PATH_GRAPHICS = "buttonGraphics/";	
	private static final String ICON_PLAY = "Play16.gif";
	private static final String ICON_PAUSE = "Pause16.gif";
	private static final String ICON_FastForward = "FastForward16.gif";
	private static final String ICON_REPORT = "Report.gif";
	private static final String ICON_Stop = "Stop.gif";
	private static final String ICON_Parameter = "Parameter.gif";	
	private static final String ICON_Bigger = "Bigger.gif";
	private static final String ICON_Smaller = "Smaller.gif";
	private static final String TEXT_SEPS = "simulated seconds per second";
	/** Default width for the GUI window */
	public static final int WIN_DEFAULT_WIDTH = 1280;
	/** Default height for the GUI window */
	public static final int WIN_DEFAULT_HEIGHT = 720;

	public JPanel ButtonMenus;
	public JButton playButton;
	public JButton end;
	public JButton report;
	public JButton FastForward;
	public JButton parameter;
	public JButton Smaller;
	public JButton Bigger;
	private static JSplitPane JSP0;
	private static JSplitPane JSP1;
	private static JSplitPane JSP2;
	private static JSplitPane JSP3;
	protected static boolean simPaused = true;
	protected static boolean simCancelled = false;
    private JFileChooser chooser;
	private JTabbedPane tabs = new JTabbedPane();
	private JDesktopPane desktopPane;
	
	public ActionListener e;

	private JPanel nodeStatus;//??????????
	private JInternalFrame internal2DFrame;
	private JInternalFrame internal3DFrame;
	private JPanel fileMenus;
	private JPanel NodeList;
    public final JMenuItem[] items = {
		  	new JMenuItem("????????????"), new JMenuItem("????????"), new JMenuItem("2D????????"), new JMenuItem("????????"),
			new JMenuItem("????"), new JMenuItem("????????"),new JMenuItem("3D????????"), new JMenuItem("????"),
    };
    private List<DTNHost> hosts;
    private List<JButton> nodeButton;
    private InfoPanel infoPanel;
    private JPanel desktop;

    /**3D??2D????????**/
    private moveEarth Orbit_3D;
    private Play Orbit_2D;
    
	/** simtime of last UI update */
    private JLabel sepsField;	// simulated events per second field
	private long lastUpdate;
	private static final int EPS_AVG_TIME = 2000;
	private double lastSimTime;
    
	public Main_Window(InfoPanel infoPanel){//EventLog elp, List<DTNHost> hosts) {
		super("????????????");
		
		this.infoPanel = infoPanel;
		this.sepsField = new JLabel("0.00");
		this.sepsField.setToolTipText(TEXT_SEPS);
		JLabel time = new JLabel("????????:");
		JLabel s = new JLabel("s");
		
		final String liquid =  "javax.swing.plaf.nimbus.NimbusLookAndFeel";
	  	try {
			UIManager.setLookAndFeel(liquid);
		} catch (ClassNotFoundException | InstantiationException
				| IllegalAccessException | UnsupportedLookAndFeelException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}			//	????????
	  	this.getContentPane().setBackground(Color.lightGray);			// ????????????
	  	
	  	
		setSize(WIN_DEFAULT_WIDTH,WIN_DEFAULT_HEIGHT);
	    desktop = new JPanel();
	    getContentPane().add(desktop);
	    
        chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File("."));
	    final JMenu[] menus = {
	    		new JMenu("????   "),new JMenu("????   "),
				new JMenu("????????   "),new JMenu("????   "),
	    };

	    items[2].setEnabled(false);
	    items[6].setEnabled(false);//??????????????????????3D??2D????????????????????
	    
	    for (int i=0;i<items.length; i++){
	    	this.items[i].addActionListener(new MenuActionListener());//????????????????????
			menus[i%4].add(items[i]);
	    };
	    JMenuBar mb = new JMenuBar();
	    for (JMenu jm:menus){
	    	mb.add(jm);
	    };
	    this.fileMenus = new JPanel();
	    this.fileMenus.setLayout(new GridLayout(2,1));
	    this.fileMenus.add(mb);
	    
	    //????????????????????,????????????????????????????
	    ButtonMenus = new JPanel();
	    ButtonMenus.setLayout(new BoxLayout(ButtonMenus, BoxLayout.X_AXIS));
	    playButton = addButton(simPaused ? ICON_PLAY : ICON_PAUSE);
	    playButton.addMouseMotionListener(new MouseAdapter(){  
	       public void mouseMoved(MouseEvent e) {  
	    	   if(simPaused == true){
		    	   playButton.setToolTipText("????????");
	           }  	    		  
	    	   else{
				   playButton.setToolTipText("????????");
	    	   }
	    	   }
	       }
	    );  
	    
	    end = new JButton();
	    end.addActionListener(this);
	    end.setIcon(createImageIcon(ICON_Stop));
	    end.addMouseMotionListener(new MouseAdapter(){  
		       public void mouseMoved(MouseEvent e) {  
		    	   end.setToolTipText("????????");
		           }  
		});
	    //end.setContentAreaFilled(false);
	    
	    report = new JButton();
	    report.setIcon(createImageIcon(ICON_REPORT));
	    //report.setContentAreaFilled(false);
	    //report.addActionListener(this);
	    report.addActionListener(new OpenActionListener());
	    report.addMouseMotionListener(new MouseAdapter(){  
		       public void mouseMoved(MouseEvent e) {  
		    	   report.setToolTipText("????????");
		           }  
		});
	    
	    
//	    FastForward = new JButton();
//	    FastForward.setIcon(createImageIcon(ICON_FastForward));
//	    FastForward.addActionListener(this);
//	    FastForward.addMouseMotionListener(new MouseAdapter(){  
//		       public void mouseMoved(MouseEvent e) {  
//		    	   FastForward.setToolTipText("????");
//		           }  
//		});
//	    
//	    Smaller = new JButton();
//	    Smaller.setIcon(createImageIcon(ICON_Smaller));
//	    Smaller.addActionListener(this);
//	    Smaller.addMouseMotionListener(new MouseAdapter(){  
//		       public void mouseMoved(MouseEvent e) {  
//		    	   Smaller.setToolTipText("????");
//		           }  
//		});
//	    
//	    Bigger = new JButton();
//	    Bigger.setIcon(createImageIcon(ICON_Bigger));
//	    Bigger.addActionListener(this);
//	    Bigger.addMouseMotionListener(new MouseAdapter(){  
//		       public void mouseMoved(MouseEvent e) {  
//		    	   Bigger.setToolTipText("????");
//		           }  
//		});
	    
	    parameter = new JButton();
	    parameter.setIcon(createImageIcon(ICON_Parameter));
	    //parameter.setContentAreaFilled(false);
        parameter.addActionListener(new ActionListener() {	//??????????????????????????????
            public void actionPerformed(ActionEvent e) {
                new RouterInfo();
            }
        });
        parameter.addMouseMotionListener(new MouseAdapter(){  
		       public void mouseMoved(MouseEvent e) {  
		    	   parameter.setToolTipText("????????");
		           }  
		});
        
	    ButtonMenus.add(end);
//	    ButtonMenus.add(FastForward);
//	    ButtonMenus.add(Smaller);
//	    ButtonMenus.add(Bigger);
	    ButtonMenus.add(parameter);
	    ButtonMenus.add(report);
	    ButtonMenus.add(Box.createHorizontalStrut(18));
	    
	    JSeparator sep = new JSeparator(SwingConstants.VERTICAL);	//??????????
	    sep.setPreferredSize(new Dimension(20,20));
	    sep.setMaximumSize(new Dimension(20,20));
	    sep.setMinimumSize(new Dimension(20,20));
	    ButtonMenus.add(sep);
	    
	    ButtonMenus.add(time);										//????????????
	    ButtonMenus.add(Box.createHorizontalStrut(3));
	    ButtonMenus.add(sepsField);
	    ButtonMenus.add(Box.createHorizontalStrut(3));
	    ButtonMenus.add(s);

	    fileMenus.add(ButtonMenus);
	  
	    //---------------------------????????????----------------------------//	  	
	    this.NodeList = new JPanel();
	    this.NodeList.setBorder(new TitledBorder("????????"));

	  

		desktopPane = new JDesktopPane();
		//desktopPane.setBackground(Color.LIGHT_GRAY);
		//System.out.println(desktopPane.getBackground());
	    
	    //---------------------------????????????----------------------------//
	    JPanel Event = new JPanel();
        Event.setLayout(new BoxLayout(Event,BoxLayout.Y_AXIS));						//	????Y??????????
		//Event.setBorder();
	    Event.setBorder(new TitledBorder("????????"));
	    
	    
	    //	????splitPane1??????????????0.1????????splitPane1????????????
	    JSP1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT,false,desktopPane, Event);
	    JSP1.setResizeWeight(0.7);													//	????splitPane1??????????????0.1????????splitPane1????????????
	    
	    JScrollPane Jscrollp = new JScrollPane(NodeList);		
	    Jscrollp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);	//	??????????????
	    JSP2 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,false,JSP1,Jscrollp);
	  	JSP2.setResizeWeight(0.97);
	  	JSP3 = new JSplitPane(JSplitPane.VERTICAL_SPLIT,false,fileMenus,JSP2);	
	  	JSP3.setResizeWeight(0.01);

	  	add(JSP3);
	}
	/**
	 * ????????????????????????????????????????????UI????????????????????????
	 * @param eventLog
	 */
	public void resetEventLog(EventLog eventLog){
		this.infoPanel.setBackground(JSP1.getBackground());	
	    this.infoPanel.setBorder(new TitledBorder("????????"));
	    JSP0 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,false,new JScrollPane(eventLog),new JScrollPane(this.infoPanel));
	    JSP0.setResizeWeight(0.6);
		this.JSP1.setBottomComponent(JSP0);
//		this.JSP1.setBottomComponent(new JScrollPane(eventLog));
	}

	/**
	 * ????????????????????????????????????????????UI????????????????????
	 * @param hosts
	 */
	public void setNodeList(List<DTNHost> hosts){
		DTNHost.reset();//????DTNHost????????????????????????
		
		this.nodeButton = new ArrayList<JButton>();
		this.hosts = hosts;//??????????????????????
	    this.NodeList = new JPanel();
	    this.NodeList.setLayout(new GridLayout(hosts.size(), 1));
	    this.NodeList.setBorder(new TitledBorder("????????"));
	    for (int i = 0; i < hosts.size(); i++){
	    	JButton nodeButton = new JButton(hosts.get(i).toString());
	    	this.nodeButton.add(nodeButton);//????????????????????????????????
	    	nodeButton.addActionListener(this);
	    	this.NodeList.add(nodeButton);
	    }
	    JScrollPane Jscrollp = new JScrollPane(NodeList);		
	    Jscrollp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);	//	??????????????
	    JSP2.setRightComponent(Jscrollp);
	}
	
	/**
	 * ??????????UI????????message????host??????
	 * @param host
	 */
	public void newInfoPanel(DTNHost host){
		this.infoPanel.setBackground(JSP1.getBackground());	
		this.infoPanel.showInfo(host);
	    this.infoPanel.setBorder(new TitledBorder("????????"));
//		desktopPane.add(this.infoPanel);
	    JSP0.setRightComponent(new JScrollPane(this.infoPanel));		// ??EventLog??????????
	}
	/**
	 * ??????????????????????????UI??????3D??2D??????????????
	 * @param hosts
	 */
	public void set3DWindow(){
		desktopPane.removeAll();
		desktopPane.setBackground(Color.LIGHT_GRAY);

	    //---------------------------????????????----------------------------//	  	
		internal3DFrame = new JInternalFrame("????????", true, true, true, true);
		internal3DFrame.setLocation(0, 0);
		internal3DFrame.setSize(500, 300);
		internal3DFrame.setVisible(true);
		
		Orbit_3D = new moveEarth();
		Orbit_3D.init(hosts);
		//NEW ADD
		new Thread(Orbit_3D).start();//??????????????????
		//NEW ADD
		
	    internal3DFrame.getContentPane().add(Orbit_3D);
	    desktopPane.add("????????",internal3DFrame);
	    
	    //---------------------------????????????----------------------------//	  	
	    
	    internal2DFrame = new JInternalFrame("????????", true, true, true, true);
		internal2DFrame.setLocation(500, 0);
		internal2DFrame.setSize(500, 300);
		internal2DFrame.setVisible(true);

		Orbit_2D = new Play(Orbit_3D.BL,hosts.size()); //??????????hosts.size()??
		Orbit_2D.init();
		/**????????????**/
		Orbit_2D.zoom(internal2DFrame.getWidth(), internal2DFrame.getHeight());
		/**????????????**/
		new Thread(Orbit_2D.getJP()).start();  //??????????????????????????
	    internal2DFrame.getContentPane().add(Orbit_2D);
	    desktopPane.add("????????",internal2DFrame);
	    
	    internal2DFrame.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
		        Orbit_2D.zoom(internal2DFrame.getWidth(), internal2DFrame.getHeight());
			}
		});
	    
	}
	/**
	 * ????????????3D????????????????????????????????????????????????????
	 */
	public void refresh3DWindow(){
		desktopPane.remove(internal3DFrame);
		internal3DFrame.setLocation(0, 0);
		internal3DFrame.setSize(500, 300);
		internal3DFrame.setVisible(true);
		desktopPane.add("????????",internal3DFrame);
	}
	/**
	 * ????????????2D????????????????????????????????????????????????????
	 */
	public void refresh2DWindow(){
		desktopPane.remove(internal2DFrame);
		internal2DFrame.setLocation(500, 0);
		internal2DFrame.setSize(500, 300);
		internal2DFrame.setVisible(true);
		desktopPane.add("????????",internal2DFrame);
	}
	/**
	 * ????????????????
	 */
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == this.playButton) {
			setPaused(simPaused);
		}
		else if (e.getSource() == this.end){		
			setPaused(false); 
			this.simPaused = true;
			this.simCancelled = true;
			//System.exit(0);
			this.setSimTime(0);			//????????????
		}
		else{
			for (int i = 0; i < hosts.size(); i++){
				if (e.getSource() == this.nodeButton.get(i)){
					newInfoPanel(this.hosts.get(i));
					break;
				}
			}
		}
	}
	
	/**
	 * ????????????????
	 */
	 class MenuActionListener implements ActionListener{
		 public void  actionPerformed(ActionEvent e){
			 switch(((JMenuItem)e.getSource()).getText()){
				 case "????????????":{
			          	JFileChooser fileChooser = new JFileChooser("");
			            fileChooser.setDialogTitle("????????????");
			            FileNameExtensionFilter filter = new FileNameExtensionFilter("TEXT FILES", "txt", "text");
			            fileChooser.setFileFilter(filter);
			            JLabel label = new JLabel();
			            int n = fileChooser.showOpenDialog(fileChooser);
			            if (n == fileChooser.APPROVE_OPTION){
			                String input =fileChooser.getSelectedFile().getPath();
			                new AddChartFrame(new Loadtxt(input));
			            }
			            break;
				 }
				 case "????":{
					 System.exit(0);//????????
				 }
				 case "????":{
					 JOptionPane.showMessageDialog(null, "The copy right is resevered by USTC, Infonet Lab \n"
					 		+ "The code is written based on THE ONE ", "????", JOptionPane.YES_OPTION);
					 break;
				 }
				 case "????????":{
					 JOptionPane.showMessageDialog(null, "The code is powered by USTC, Infonet Lab"
					 		, "????????", JOptionPane.YES_OPTION);
					 break;
				 }
				 case "3D????????":{
					 //set3DWindow();
					 refresh3DWindow();
					 break;
				 }
				 case "2D????????":{
					 refresh2DWindow();
					 break;
				 }
				 case "????????":{
			            JFileChooser fileChooser = new JFileChooser("reports//");
			            fileChooser.setDialogTitle("????????????");
			            FileNameExtensionFilter filter = new FileNameExtensionFilter("TEXT FILES", "txt", "text");
			            fileChooser.setFileFilter(filter);
			            JLabel label = new JLabel();
			            int n = fileChooser.showOpenDialog(fileChooser);
			            if (n == fileChooser.APPROVE_OPTION){
			                String input =fileChooser.getSelectedFile().getPath();
			                new AddChartFrame(new Loadtxt(input));			           
			            }
			            break;
				 }
				 case "????????":{
					 new RouterInfo();
					 break;
				 }
			 }
		 }
	 }
	 /**
	  * ??????????????????????
	  */
    class OpenActionListener implements ActionListener{
        public void  actionPerformed(ActionEvent e){
            JFileChooser fileChooser = new JFileChooser("reports//");
            fileChooser.setDialogTitle("????????????");
            FileNameExtensionFilter filter = new FileNameExtensionFilter("TEXT FILES", "txt", "text");
            fileChooser.setFileFilter(filter);
            JLabel label = new JLabel();
            int n = fileChooser.showOpenDialog(fileChooser);
            if (n == fileChooser.APPROVE_OPTION){
                String input =fileChooser.getSelectedFile().getPath();
                new AddChartFrame(new Loadtxt(input));
            }
        }
    }
    
	@Override
	public void stateChanged(ChangeEvent e) {
		// TODO Auto-generated method stub
		
	}
	
	public boolean getSimCancelled(){
		return this.simCancelled;
	}
	public boolean getPaused(){
		return this.simPaused;
	}
	
	private ImageIcon createImageIcon(String path) {
		java.net.URL imgURL = getClass().getResource(PATH_GRAPHICS+path);
		return new ImageIcon(imgURL);
	}
	
	private JButton addButton(String iconPath) {
		JButton button = new JButton(createImageIcon(iconPath));
		button.addActionListener(this);
		//button.setContentAreaFilled(false);
		ButtonMenus.add(button);
		return button;
	}
	/**
	 * ??????????????????????????????????simCancelled??????????????????????
	 */
	public void resetSimCancelled(){
		if (this.simCancelled == true)
			this.simCancelled = false;
	}
	/**
	 * Sets simulation to pause or play.
	 * @param paused If true, simulation is put to pause
	 */
	public void setPaused(boolean paused) {
		if (!paused) {//????????
			this.playButton.setIcon(createImageIcon(ICON_PLAY));
			this.simPaused = true;
			Orbit_3D.setFlag(false);
			Orbit_2D.setFlag(false);
		}
		else {//????????
			this.playButton.setIcon(createImageIcon(ICON_PAUSE));
			this.simPaused = false;
			Orbit_3D.setFlag(true);
			Orbit_2D.setFlag(true);
		}
	}
	
	/**
	 * ????????????
	 * @param time The time to show
	 */
	public void setSimTime(double time) {
		long timeSinceUpdate = System.currentTimeMillis() - this.lastUpdate;
		
		if (timeSinceUpdate > EPS_AVG_TIME) {
			double val = ((time - this.lastSimTime) * 1000)/timeSinceUpdate;
			String sepsValue = String.format("%.2f 1/s", val);

			this.sepsField.setText(sepsValue);
			this.lastSimTime = time;
			this.lastUpdate = System.currentTimeMillis();
		}
		else {
			this.sepsField.setText(String.format("%.1f", time));
		}
		
		if(time == 0){
			String sepsValue = String.format("%.2f 1/s", time);
			this.sepsField.setText(sepsValue);
		}
	}
}






