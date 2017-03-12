/*
  CMPT 340 Assignment 4 - AKKA Concurrency
  Andrew Magnus
  11140881
  amm215
  
 Problem 2 (70 Points): Stable Marriage Problem.
 Let Man and Woman each be arrays of n processes.  
 Each man ranks the women from 1 to n, and each woman ranks the men from 1 to n.
 A pairing is a one-to-one correspondence of men and women.
 A pairing is stable if, for two men m1 and m2 and their paired women w1 and w2, 
 both of the following conditions are satisfied:
	m1 ranks w1 higher than w2 or w2 ranks m2 higher than m1;
	and
	m2 ranks w2 higher than w1 or w1 ranks m1 higher than m2.
Expressed differently, a pairing is unstable if a man and woman would both prefer
each other to their current pair.  A solution to the stable marriage problem is a
set of n pairings, all of which are stable.
Write an Actors-based program using Java with Akka to simulate a solution to the
stable marriage problem.  The processes should communicate using asynchronous message
passing.  The men should propose and the women should listen.  
A woman has to accept the first proposal she gets, because a better one
might not come along; however, she can dump the first man if she later 
gets a better proposal.

Solution Description:
This solution uses Men and Women Akka actors, the men send proposals to the women, and the women send
replies, which are rejections or acceptances, when everyone has a match, the problem ends.
The main Program runs through a series of steps outlined here:
	Step 0: set up the actor system and initialize the arrays of men and women actors 
			(create randomized preference lists)
	Step 1: Signal to the Men that they can start
	Step 2: Actively ask all the men if they are paired up or not, when they are 
			all paired the simulation is complete.
	Step 3: Separately ask the men and women who they think their spouse is 
	Step 4:	Check to see that all the men and women are in sync
			i.e. m1 records hes married to w1 but w1 records shes married to m2
	Step 5: do exhaustive/brute force testing to make sure every match is stable
	
	NOTE Simulation is successful but a bit slow with more than a couple hundred actors, right now it
	is set to run with 10 actors. But works and is successful with 100 or more easy 
 */
package Problems;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import akka.actor.Props;
import akka.actor.UntypedActor;
import scala.concurrent.duration.Duration;
public class Problem2 {
	//---------------------------ACTOR CLASSES---------------------
	/**
	 * Abstract class that holds perferenceList and setup information for both the ManActor
	 * and WomanActor classes because they are both the same for these basics.
	 * Setup takes in an array of ActorRefs and creates a shuffled version for the peferenceList
	 * */
	public static abstract class PersonActor extends UntypedActor{
		//represents a persons ranked list of preferred spouses, with index 0 being most preferred
		List<ActorRef> preferenceList;
		//this version is unchanged from the start as  the men Actors remove from the preferenceList
		//Used in TESTING
		List<ActorRef> staticPrefList;
		//represents the persons current partner actor
		ActorRef currentMatch;
		/*
		 * Takes in the actor list of possible spouses and creates a random preference list for the actor
		 */
		protected void setup(ActorRef[] possibleSpouses){
			//unshuffledList
			this.preferenceList = new ArrayList<ActorRef>();
			this.staticPrefList = new ArrayList<ActorRef>();
			this.preferenceList.addAll(Arrays.asList(possibleSpouses));
			Collections.shuffle(this.preferenceList);
			this.staticPrefList.addAll(this.preferenceList);//we need a unedited copy for testing
			//set currentMatch to Null
			this.currentMatch = null;
		}
		/*
		 * Prints out the preference list in a slightly more readable manner
		 */
		public String prettyPrintPrefs(){
			String returnMess = getSelf().path().name()+" preferenceList: ";
			for (int i = 0; i < this.preferenceList.size(); i++)
			{
				returnMess = returnMess+this.preferenceList.get(i).path().name()+", ";
			}
			return returnMess;
		}
	}
	/**
	 ManActor class encapsulates the man of the stable marriage problem, these actors send out ProposalMessages
	 to WomanActors based on their preferenceList, the WomenActors can RejectProposal or AcceptProposal the Proposals
	 */
	public static class ManActor extends PersonActor{
		@Override
		public void onReceive(Object message) throws Throwable {
			//this will setup the preferenceList and current match
			if(message instanceof SetupMessage){
				//sends the woman list to setup
				this.setup((ActorRef[])((SetupMessage)message).possibleSpouses);
				getSender().tell(new ReadyMessage(), getSelf());
			//Man actors start the process by sending out proposals to the first person on their preference list
			}else if (message instanceof StartMessage){
				sendProposal();
			}
			//the last proposal was accepted, if this happens, set sender to current match
			//do NOT send out any more proposals
			else if (message instanceof AcceptanceMessage){
				this.currentMatch = getSender();
			//last proposal was rejected, try sending proposal to the next person on the list
			}else if (message instanceof RejectionMessage){
				sendProposal();
			//returns true or false if you currently have a match
			}else if (message instanceof AreYouMatchedMessage){
				Boolean reply = new Boolean((this.currentMatch != null));
				getSender().tell(reply, getSelf());
			//returns your current match and preferences for TESTING
			}else if (message instanceof WhosYourMatchAndPrefsMessage){
				getSender().tell(new MatchAndPrefsMessage(this.currentMatch, this.staticPrefList), getSelf());
			}else{
				//Don't know the request
				unhandled(message);
			}
		}
		/*
		 Sends proposal to the first person on the preference list, and then removes them from the list
		 so that current highest preference is always at 0
		 */
		public void sendProposal(){
			if(preferenceList.isEmpty() == false){
				ActorRef currentPref = preferenceList.get(0);//get the first woman in the list
				//sends proposal message to their current preference
				currentPref.tell(new ProposalMessage(), getSelf());
				preferenceList.remove(0);//removes them from the list so we only send them one proposal
			}
		}
	}
	/**
	 Encapsulates the women of the Stable Marriage Problem,  these actors are set up with a random preference
	 list and simply receive proposal messages from the Men actors, if the suitor is the first one or higher on 
	 the womans preference list the proposal will be accepted, otherwise it will be rejected
	 * */
	public static class WomanActor extends PersonActor{
		@Override
		public void onReceive(Object message) throws Throwable {
			//sets up the preference list and current match
			if(message instanceof SetupMessage){
				//sends the woman list to setup
				this.setup((ActorRef[])((SetupMessage)message).possibleSpouses);
				getSender().tell(new ReadyMessage(), getSelf());
			//handle proposal message
			} else if (message instanceof ProposalMessage){
				ActorRef suitor = getSender();
				//if the woman has no current match, accept the current suitor
				if(currentMatch == null){
					acceptProposal(suitor);
				}
				//current match is not null, so we need to check if the current is higher preference or not
				else{
					if(isCurrentPerferredOver(suitor)){
						//current match is preferred so reject suitor
						rejectProposal(suitor);
					}else{
						//suitor is preferred over current
						//We must reject the current AND accept the suitor
						rejectProposal(this.currentMatch);
						acceptProposal(suitor);
					}
				}	
			//Send the current match and list back for TESTING
			}else if (message instanceof WhosYourMatchAndPrefsMessage){
				getSender().tell(new MatchAndPrefsMessage(this.currentMatch, this.staticPrefList), getSelf());
			}else{
				//Don't know the request
				unhandled(message);
			}
		}
		/*
		 Returns a boolean true if the current match is more preferred over the proposing suitor 
		 */
		public boolean isCurrentPerferredOver(ActorRef suitor){
			int currentMatchIndex = preferenceList.indexOf(this.currentMatch);
			int suitorIndex = preferenceList.indexOf(suitor);
			//if the index of current is less than suitor that means its higher on the list
			if(currentMatchIndex < suitorIndex){
				return true;
			}else{
				return false;
			}
		}
		/*
		 Sets the current match to the suitor ref and sends an acceptance message
		 */
		public void acceptProposal(ActorRef suitor){
			this.currentMatch = suitor;
			suitor.tell(new AcceptanceMessage(), getSelf());
		}
		/*
		 Sends a rejection message to the suitor 
		 */
		public void rejectProposal(ActorRef suitor){
			suitor.tell(new RejectionMessage(), getSelf());
		}
	}
	//--------------------------MESSAGE CLASSES--------------------
	/**
	 * This message will be sent to every actor before the matching process starts so that
	 * everyone can set up their preference list before the problem starts. Each actor will
	 * will get a reference to the array of the opposite gender and create a randomized preference list
	 * */
	public static class SetupMessage implements Serializable{
		private static final long serialVersionUID = 1L;
		ActorRef[] possibleSpouses;
		public SetupMessage(ActorRef[] list){
			this.possibleSpouses = list;
		}
	}
	/**
	 * return message that is sent by all actors after they are finished setting up 
	 */
	public static class ReadyMessage implements Serializable {
		private static final long serialVersionUID = 1L;	
	}
	/**
	 These messages are sent by the ManActors to WomanActors based on their preferenceList
	 Woman Actors respond with RejectionMessage or AcceptanceMessages based on their preferenceList.
	 */
	public static class ProposalMessage implements Serializable{
		private static final long serialVersionUID = 1L;
	}
	/**
	 sent by the main program to each of the ManActors, this signals the start of the simulation,
	 each man actor will begin sending out proposals to their preferences.
	 */
	public static class StartMessage implements Serializable{
		private static final long serialVersionUID = 1L;
	}
	/**
	 One of two possible messages sent from WomanActors after a proposalMessage, this message
	 signals rejection of the offer, the ManActor will not update his currentMatch and continue to 
	 send out proposals
	 */
	public static class RejectionMessage implements Serializable{
		private static final long serialVersionUID = 1L;
	}
	/**
	 One of two possible messages sent from WomanActors after a proposalMessage, this message
	 signals acceptance of the offer, the ManActor will update his currentMatch and halt sending out any
	 more proposals
	 */
	public static class AcceptanceMessage implements Serializable{
		private static final long serialVersionUID = 1L;
	} 
	/**
	 Sent out periodically to all the men, if all the men have a match then the simulation is over
	 */
	public static class AreYouMatchedMessage implements Serializable{
		private static final long serialVersionUID = 1L;
	}
	/**
	 Sent out to ask an actor for its currentMatch (spouse) and their original Preference list
	 */
	public static class WhosYourMatchAndPrefsMessage implements Serializable{
		private static final long serialVersionUID = 1L;
	}
	/**
	 Intended as the reply to WhosYourMatchAndPrefsMessage, 
	 contains the actors current match and preference list
	 */
	public static class MatchAndPrefsMessage implements Serializable{
		private static final long serialVersionUID = 1L;
		ActorRef match;
		List<ActorRef> prefs;
		public MatchAndPrefsMessage(ActorRef m, List<ActorRef> p ){
			this.match = m;
			this.prefs = p; 
		}
	}
	//--------------------------TESTING CLASSES------------------
	/**
	 The Match class encapsulates a marriage, each marriage contains a man and a woman actor
	 and each of these people has a preference list
	 */
	public static class Match{
		ActorRef man;
		ActorRef woman;
		List<ActorRef> manPrefs;
		List<ActorRef> womanPrefs;
		public Match(ActorRef m, ActorRef w,List<ActorRef> mp, List<ActorRef> wp){
			this.man = m;
			this.woman = w;
			this.manPrefs = mp;
			this.womanPrefs = wp;
		}
		//better toString method thats less messy
		@Override
		public String toString(){
			String manName =man.path().name();
			String womanName = woman.path().name();
			return manName+" marries "+ womanName 
					+ "\n"+prettyPrintList(manName,this.manPrefs)
					+ "\n"+prettyPrintList(womanName, this.womanPrefs);
		}
		private String prettyPrintList(String name, List<ActorRef> l){
			String returnMess = name+" preferenceList: ";
			for (int i = 0; i < l.size(); i++)
			{
				returnMess = returnMess+l.get(i).path().name()+", ";
			}
			return returnMess;
		}
		//override equals method so we can use ArrayList.contains() in TEST 1
		//this checks equality based on the string names, not the references (cause clones)
		//NOTE this only looks at equality of the man and woman names it does NOT check their 
		//preferenceLists
		@Override
		public boolean equals(Object other){
		    if (other == null) return false;
		    if (other == this) return true;
		    if (!(other instanceof Match))return false;
		    Match x = (Match)other;
			return ((this.man.path().name() == x.man.path().name()) && (this.woman.path().name() == x.woman.path().name()));
		}
	}
	/*
	 returns the index of the given person in the list of matches, this function will look in both the 
	 man and woman spots for the match
	 used for TESTING
	 */
	public static int findIndexOf(ActorRef person, List<Match> matches){
		for(int i = 0; i < matches.size(); i++)
		{
			Match m = matches.get(i);
			if((person.path().name() == m.man.path().name()) || (person.path().name() == m.woman.path().name())){
				return i;
			}
		}
		return -1;
	}
	//---------------------------MAIN-----------------------------
	public static void main(String[] args) {
		//Set up the default values for the simulation
		int N_COUPLES = 10;
		int TIMEOUT_DURATION_SEC = 3;
		//arrays to hold the references to all the men and women actors
		ActorRef[] men = new ActorRef[N_COUPLES];
		ActorRef[] women = new ActorRef[N_COUPLES];
		//after the simulation is done, each match will be stored a Match so they can be TESTED
		List<Match> menMatches = new ArrayList<Match>();
		List<Match> womenMatches = new ArrayList<Match>();
		//----------------------SETUP--------------------------
		//Step 0: Setup
		// Create an actor system
		final ActorSystem marSystem = ActorSystem.create("marriageProblem");
		// Create an inbox (actor in a box), allows our main function to send
		// and receive messages without being an actor itself
		final Inbox client = Inbox.create(marSystem);
		//create all the actors (men and women)
		for(int i = 0; i < N_COUPLES; i++){
			men[i] = marSystem.actorOf(Props.create(ManActor.class), "man-"+i);
			women[i] = marSystem.actorOf(Props.create(WomanActor.class), "woman-"+i);
		}
		//setup the men
		//send setup messages to each actor and wait for a reply, this ensures that all the actors are set up before we start the simulation 
		for(int i = 0; i < men.length; i++){
			// send message to setup
			client.send(men[i], new SetupMessage(women.clone())); //CLONES  THE ARRAY SO WE ARE NOT PASSING AROUND A REFERENCE TO THE ORIGINAL ARRAY
			try {
				client.receive(Duration.create(TIMEOUT_DURATION_SEC, TimeUnit.SECONDS));
			}
			catch(TimeoutException e){
				System.out.println("timeout waiting for reply from Man:"+men[i].path().name());
			}
		}
		//Setup the Women
		//send setup messages to each actor and wait for a reply, this ensures that all the actors are set up before we start the simulation 
		for(int i = 0; i < women.length; i++){
			// send message to setup
			client.send(women[i], new SetupMessage(men.clone())); //CLONES THE ARRAY SO WE ARE NOT PASSING AROUND A REFERENCE TO THE ORIGINAL ARRAY
			try {
				client.receive(Duration.create(TIMEOUT_DURATION_SEC, TimeUnit.SECONDS));
			}
			catch(TimeoutException e){
				System.out.println("timeout waiting for reply from woman: "+women[i]);
			}
		}
		//------------------START THE PROBLEM RUNNING-----------------------
		//Step 1: start off by telling each of the men that they can start
		for(int i = 0; i < N_COUPLES; i++){
			men[i].tell(new StartMessage(), ActorRef.noSender());
		}
		//Step 2: Ask the Men to see if they all have matches, if they all do, then the solving is complete
		boolean allDone = false;
		while(allDone == false)
		{
			allDone = true;
			//ask each of the men if they are currently matched, if everyone is matched we are done
			for(int i = 0; i < men.length; i++){
				client.send(men[i], new AreYouMatchedMessage());
				Boolean reply = null;
				try {
					reply = (Boolean) client.receive(Duration.create(TIMEOUT_DURATION_SEC, TimeUnit.SECONDS));
					if(reply == false){
						allDone = false;
					}
				}
				catch(TimeoutException e){
					System.out.println("timeout waiting for reply from"+men[i]);
				}
			}
		}
		//Step 3: Find out who everyones new spouses are!
		//Here we are going through the list of men and women SEPERATELY and creating matches
		//this is so we can test that all the actors are in sync with each other
		//i.e. woman-1 says her current match is man-1 but man-1 says his current match is woman-2 is bad
		//fill the womenMatches List
		for(int i = 0; i < women.length; i++){
			//ask each woman for their preference list and spouse and create a Match Object
			client.send(women[i], new WhosYourMatchAndPrefsMessage()); 
			MatchAndPrefsMessage reply = null;
			try {
				reply = (MatchAndPrefsMessage) client.receive(Duration.create(TIMEOUT_DURATION_SEC, TimeUnit.SECONDS));
				womenMatches.add(new Match(reply.match, women[i], null, reply.prefs));//husbands pref List is currently null cause we dont have that information yet
			}
			catch(TimeoutException e){
				System.out.println("timeout waiting for reply from woman: "+women[i]);
			}
		}
		//fill the menMatches List
		for(int i = 0; i < N_COUPLES; i++){
			//ask each man for their preference list and spouse and create a Match Object
			client.send(men[i], new WhosYourMatchAndPrefsMessage()); 
			MatchAndPrefsMessage reply = null;
			try {
				reply = (MatchAndPrefsMessage) client.receive(Duration.create(TIMEOUT_DURATION_SEC, TimeUnit.SECONDS));
				menMatches.add(new Match(men[i], reply.match, reply.prefs, null));//wifes pref List is currently null cause we dont have that information yet
			}
			catch(TimeoutException e){
				System.out.println("timeout waiting for reply from man: "+men[i]);
			}
		}
		//Step 4: Testing
		//----------------TESTING SUITE----------------
		//Here we are verifying that the separate lists we created in Step 3 match up and that all actors
		//are in sync about who their spouses should be.		
		//for each match in man list, we are checking to see if that same pairing shows up in
		//the other list, if it does then everything is in sync
		boolean test1Successful = true;
		for(int i = 0; i < menMatches.size(); i++){
			Match currentMatch = menMatches.get(i);
			//the pair does not show up in the other list
			if(womenMatches.contains(currentMatch) == false){
				System.out.println("Test 1 Failure - bad MATCH: "+currentMatch);
				test1Successful = false;
			}
		}
		//Step 4.5: in order to prepare for the second test, we are going to create a masterMatch list, this
		//set of matches will have both preferenceLists for both spouses in it. (previously one was set to null)
		//setup for test2, we need a master list of matches where both preference lists are available.
		//so we are going to merge them
		System.out.println("----------------------PRINT MARRIAGES AND PRFERENCE LISTS----------------------");
		List<Match> masterMatches = new ArrayList<Match>();
		for(int i = 0; i < menMatches.size(); i++){
			Match currentMatch = menMatches.get(i);
			int womenIndex = womenMatches.indexOf(currentMatch); 
			masterMatches.add(new Match(currentMatch.man, currentMatch.woman, currentMatch.manPrefs, womenMatches.get(womenIndex).womanPrefs));
			System.out.println(masterMatches.get(i));
		}
		//STEP 5: This second test will do an exhaustive (and brute force) check of each and every match to make sure its stable
		//TEST 2 : check stability. Now that we have checked that the matches are all the same, we can simplify by only using one of the match lists
		//this test will be an exhaustive test of postconditions of the stable marriage problem.
		//--FROM ASSIGNMENT SPEC--
			//Expressed differently, a pairing is unstable if a man and woman would both prefer
			//each other to their current pair.  A solution to the stable marriage problem is a
			//set of n pairings, all of which are stable.
		//so for every man or woman on a persons preference list that is higher preference than their spouse
		//(in our case, with a smaller index) we need to check THAT persons preference list to see if 
		//the person appears above THAT persons current match, if it does then they both prefer each other and the match is UNSTABLE
		//test the mens lists first
		//starting from most preferred women to least preferred until the wife is reached
		//check each womans preference list to see if the current man being checked is higher than
		//the womans husband on her list.
		boolean test2Successful = true;
		for(int i = 0; i < masterMatches.size(); i++){
			//grab the current mans preference list and wifes name
			List<ActorRef> myPrefList = masterMatches.get(i).manPrefs;
			String wifeName = masterMatches.get(i).woman.path().name();
			//loop through everyone on the mans preference list more preferred than the wife
			for(int f = 0; f < myPrefList.size(); f++){
				//grab a woman from the list
				ActorRef testWoman = myPrefList.get(f);
				//if its the wife we stop
				if(testWoman.path().name() == wifeName){
					break;
				}
				//we need to check our test womans preference list, if our man is on that list with
				//higher preference the womans husband we found an unstable match
				int x = findIndexOf(testWoman, masterMatches);
				Match testWomansMatch = masterMatches.get(x);
				//get the index of her husband on her list
				int herManIndex = testWomansMatch.womanPrefs.indexOf(testWomansMatch.man);
				//get the index of our man on her list
				int meIndex = testWomansMatch.womanPrefs.indexOf(masterMatches.get(i).man);
				//if our guy is more perferred (lower index) than her husband the marriage is unstable
				if(meIndex < herManIndex){	
					System.out.println("Unstable Matches Found: "+ masterMatches.get(i) +"\n and \n"+ testWomansMatch );
					test2Successful = false;
				}
			}
		}
		//test the womens lists 
		//starting from most preferred man to least preferred until the husband is reached
		//check each mans preference list to see if the current woman being checked is higher than
		//the mans wife on his list.
		for(int i = 0; i < N_COUPLES; i++){
			//grab the current womans preference list and husbands name
			List<ActorRef> myPrefList = masterMatches.get(i).womanPrefs;
			String husbandName = masterMatches.get(i).man.path().name();
			//now we check everone one the womans perference list above the husband
			for(int f = 0; f < myPrefList.size(); f++){
				//grab a man from the preference list above the husband
				ActorRef testMan = myPrefList.get(f);
				//if we reach the wife we stop
				if(testMan.path().name() == husbandName){
					break;
				}
				//we need to check our test mans preference list, if our woman is on that list 
				//ABOVE (higher preference) the test mans wife we found an unstable match
				int x = findIndexOf(testMan, masterMatches);
				Match testMansMatch = masterMatches.get(x);
				//get the index of her husband on her list
				int hisWifeIndex = testMansMatch.manPrefs.indexOf(testMansMatch.woman);
				//get the index of our woman on his list
				int meIndex = testMansMatch.manPrefs.indexOf(masterMatches.get(i).woman);
				//if our guy is higher then her husband the marriage is unstable
				if(meIndex < hisWifeIndex){
					System.out.println("Unstable Matches Found: "+ masterMatches.get(i) +"\n and \n"+ testMansMatch );
					test2Successful = false;
				}
			}
		}
		//print message for tests
		System.out.println("----------------TEST 1: Verify Matches----------------");
		if(test1Successful){
			System.out.println("Test 1 SUCCESS");
		}else{
			System.out.println("Test 1 FAILURE");
		}
		System.out.println("----------TEST 2: Check for Unstable Matches----------");
		if(test2Successful){
			System.out.println("Test 2 SUCCESS");
		}else{
			System.out.println("Test 2 FAILURE");
		}		
		//close down the actor system
		marSystem.terminate();
	}
}

/*
 Example Output of program:
 
 ----------------------PRINT MARRIAGES AND PRFERENCE LISTS----------------------
man-0 marries woman-2
man-0 preferenceList: woman-9, woman-2, woman-1, woman-5, woman-4, woman-6, woman-3, woman-0, woman-8, woman-7, 
woman-2 preferenceList: man-9, man-1, man-3, man-7, man-6, man-0, man-5, man-4, man-8, man-2, 
man-1 marries woman-5
man-1 preferenceList: woman-9, woman-5, woman-7, woman-2, woman-0, woman-1, woman-4, woman-8, woman-3, woman-6, 
woman-5 preferenceList: man-4, man-2, man-9, man-5, man-1, man-0, man-8, man-6, man-3, man-7, 
man-2 marries woman-0
man-2 preferenceList: woman-0, woman-7, woman-9, woman-8, woman-6, woman-4, woman-5, woman-1, woman-2, woman-3, 
woman-0 preferenceList: man-2, man-3, man-1, man-6, man-4, man-0, man-5, man-9, man-8, man-7, 
man-3 marries woman-9
man-3 preferenceList: woman-9, woman-7, woman-6, woman-0, woman-2, woman-5, woman-4, woman-3, woman-1, woman-8, 
woman-9 preferenceList: man-3, man-6, man-1, man-8, man-4, man-2, man-5, man-9, man-7, man-0, 
man-4 marries woman-1
man-4 preferenceList: woman-7, woman-1, woman-6, woman-2, woman-8, woman-0, woman-4, woman-5, woman-3, woman-9, 
woman-1 preferenceList: man-8, man-4, man-2, man-6, man-3, man-1, man-9, man-0, man-7, man-5, 
man-5 marries woman-8
man-5 preferenceList: woman-7, woman-8, woman-9, woman-2, woman-4, woman-5, woman-6, woman-1, woman-3, woman-0, 
woman-8 preferenceList: man-8, man-9, man-1, man-3, man-7, man-5, man-0, man-6, man-2, man-4, 
man-6 marries woman-3
man-6 preferenceList: woman-7, woman-8, woman-3, woman-4, woman-9, woman-5, woman-6, woman-0, woman-1, woman-2, 
woman-3 preferenceList: man-3, man-7, man-5, man-1, man-6, man-9, man-8, man-4, man-2, man-0, 
man-7 marries woman-6
man-7 preferenceList: woman-7, woman-6, woman-3, woman-1, woman-2, woman-5, woman-8, woman-0, woman-9, woman-4, 
woman-6 preferenceList: man-7, man-2, man-0, man-5, man-9, man-4, man-3, man-1, man-6, man-8, 
man-8 marries woman-7
man-8 preferenceList: woman-7, woman-3, woman-2, woman-8, woman-9, woman-6, woman-0, woman-1, woman-4, woman-5, 
woman-7 preferenceList: man-8, man-6, man-5, man-3, man-7, man-4, man-9, man-2, man-1, man-0, 
man-9 marries woman-4
man-9 preferenceList: woman-1, woman-4, woman-6, woman-2, woman-0, woman-3, woman-7, woman-5, woman-8, woman-9, 
woman-4 preferenceList: man-9, man-6, man-5, man-7, man-2, man-8, man-3, man-1, man-0, man-4, 
----------------TEST 1: Verify Matches----------------
Test 1 SUCCESS
----------TEST 2: Check for Unstable Matches----------
Test 2 SUCCESS
 */
 