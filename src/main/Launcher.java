package main;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

public class Launcher {

	public static void main(String[] args)
	{
		Runtime rt = Runtime.instance();

		Profile p1 = new ProfileImpl();
		ContainerController mainContainer = rt.createMainContainer(p1);
		
		AgentController rma;
		try {
			rma = mainContainer.acceptNewAgent("myRMA", new jade.tools.rma.rma());
			rma.start();
		} catch (StaleProxyException e) {
			e.printStackTrace();
		}
		

        Object[] snifferArgs = new Object[1];
        snifferArgs[0] = "buyer0;buyer1;buyer2;buyer3;buyer4;auctioneer;uberAgent0;uberAgent1;uberAgent2;uberAgent3;uberAgent4";
	    try {
			AgentController sniffer = mainContainer.createNewAgent("sniffer", "jade.tools.sniffer.Sniffer", snifferArgs);
			sniffer.start();
	    } catch (StaleProxyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    try {
			Thread.sleep(3000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		Object[] auctioneerArgs = new Object[3];
		auctioneerArgs[0] = 0.1f;
	    auctioneerArgs[1] = 30.0f;
	    auctioneerArgs[2] = 2;
	    AgentController auctioneer;
	    try {
			auctioneer = mainContainer.createNewAgent("auctioneer", "agents.AuctioneerAgent", auctioneerArgs);
			auctioneer.start();
		} catch (StaleProxyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    Object[] buyerArgs = new Object[3];
		buyerArgs[0] = 20.0f;
	    buyerArgs[1] = 0.0f;
	    buyerArgs[2] = 3.0f;
	    
	    for(int i = 0; i < 5; i++)
	    {
		    AgentController buyer;
		    try {
		    	String name = "buyer" + i;
				buyer = mainContainer.createNewAgent(name, "agents.BuyerAgent", buyerArgs);
				buyer.start();
			} catch (StaleProxyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    }
	    
	    Object[] uberArgs = new Object[3];
		uberArgs[0] = 10.0f;
	    uberArgs[1] = 0.0f;
	    uberArgs[2] = 3.0f;
	    
	    for(int i = 0; i < 5; i++)
	    {
		    AgentController seller;
		    try {
		    	String name = "uberAgent" + i;
				seller = mainContainer.createNewAgent(name, "agents.UberAgent", uberArgs);
				seller.start();
			} catch (StaleProxyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    }
	    

	    Object[] taxiArgs = new Object[3];
		taxiArgs[0] = 20.0f;
		
	    AgentController taxi;
	    try {
	    	String name = "taxiAgent1";
	    	taxi = mainContainer.createNewAgent(name, "agents.TaxiAgent", taxiArgs);
	    	taxi.start();
	    	

			taxiArgs[0] = 15.0f;
	    	name = "taxiAgent2";
	    	taxi = mainContainer.createNewAgent(name, "agents.TaxiAgent", taxiArgs);
	    	taxi.start();
		} catch (StaleProxyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
