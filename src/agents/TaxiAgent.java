package agents;

import java.util.Vector;

import agents.UberAgent.AuctionBehaviour;
import agents.UberAgent.FIPARequestInitAsk;
import agents.UberAgent.InformListeningBehaviour;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREInitiator;

public class TaxiAgent extends Agent{

	Agent agent;
	boolean asking;
	boolean waiting;
	boolean done;
	
	float price;
	
	int refuseTimeout;
	
	public void setup() {
		agent = this;
		asking = false;
		waiting = false;
		done = false;
		refuseTimeout = 0;
		
		Object[] args = getArguments();
		price = (float) args[0];

		addBehaviour(new AuctionBehaviour());
	}

	public void takeDown() {
		System.out.println(getLocalName() + ": done working.");
	}
	
	class AuctionBehaviour extends Behaviour {

		@Override
		public void action() {
			if(!asking && !waiting)
			{
				asking = true;
				addBehaviour(new FIPARequestInitAsk(agent, new ACLMessage(ACLMessage.REQUEST)));
			}
		}

		@Override
		public boolean done() {
			return (AuctioneerAgent.tradingRound > AuctioneerAgent.numRounds || done || refuseTimeout>3);
		}
	}
	
	class FIPARequestInitAsk extends AchieveREInitiator {

		public FIPARequestInitAsk(Agent a, ACLMessage msg) {
			super(a, msg);
		}

		protected Vector<ACLMessage> prepareRequests(ACLMessage msg) {
			Vector<ACLMessage> v = new Vector<ACLMessage>();
			msg.addReceiver(new AID("auctioneer", false)); //Get receivers dinamically
			String message = "ASK " + price;
			msg.setContent(message);
			v.add(msg);
			return v;
		}
		
		protected void handleAgree(ACLMessage agree) {
			System.out.println(agree);
			refuseTimeout = 0;
		}
		
		protected void handleRefuse(ACLMessage refuse) {
			System.out.println(refuse);
			refuseTimeout++;
			asking = false;
		}
		
		protected void handleInform(ACLMessage inform) {
			System.out.println(inform);
			String msgContent = inform.getContent();
			if(msgContent.contains("A transaction was made with the value"))
			{
				String[] parts = msgContent.split(":");
				float transactionValue = Float.parseFloat(parts[1]);
				AuctioneerAgent.uberTransactions.addLast(0.0f);
				AuctioneerAgent.taxiTransactions.addLast(transactionValue);
				done = true;
			}
			else if(msgContent.equals("A new offer has been registered!"))
			{
				addBehaviour(new InformListeningBehaviour());
				waiting = true;
				asking = false;
			}
		}
		
		protected void handleFailure(ACLMessage failure) {
			System.out.println(failure);
		}
	}

	class InformListeningBehaviour extends Behaviour {

		MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);

		@Override
		public void action() {
			ACLMessage msg = receive(mt);
			if(msg != null)
			{
				System.out.println(msg);
				String msgContent = msg.getContent();
				if(msgContent.contains("A transaction was made with the value"))
				{
					String[] parts = msgContent.split(":");
					float transactionValue = Float.parseFloat(parts[1]);
					AuctioneerAgent.uberTransactions.addLast(0.0f);
					AuctioneerAgent.taxiTransactions.addLast(transactionValue);
					waiting = false;
					done = true;
				}
				else if(msgContent.equals("A new offer has been registered!"))
				{
					waiting = false;
				}
			}
			else
			{
				block();
			}
		}

		@Override
		public boolean done() {
			return !waiting;
		}
	}
}
