/*
 * CMPT 340 Assignment 4 - AKKA Concurrency
 * Andrew Magnus
 * 11140881
 * amm215
 * Problem 1 (30 Points): Fibonacci Numbers. 
 * Develop an actor program for computing Fibonacci numbers concurrently.  
 * Particularly, create an actor which receives a request for a particular 
 * Fibonacci number from a client actor.  If it is one of the base cases, 
 * the actor replies with a message containing the answer; otherwise, 
 * it creates the two sub-problems you would normally create in a
 * recursive implementation.  
 * It then creates two new (Fibonacci) actors, and hands one subproblem to each.
 * Once it has received replies from these actors, it adds them to produce its result, 
 * and sends it back to the computation's client.  
 * 
 * Solution Description:
 * 		This problem is solved using child actor processes, every time a recursive call would be made a child
 * 		actor is created and sent to solve the subproblems
 * 		the function concurrentFib() creates 2 child processes, and then hands them the subproblem to solve
 * 		and then waits for a reply back from both of them, once the results are return adds them and passes back.
 */
package Problems;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.pattern.Patterns;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;


public class Problem1 {
		
	//---------------------MESSAGE CLASSES-------------------
	/**
	 * Message used to signal a FibActor to calculate the nth fib number where n = requestedFibNum
	 * also passed in is the maximum time IN SECONDS allowed for the calculation
	 * */
	public static class FibRequestMessage implements Serializable{
		private static final long serialVersionUID = 1L;
		public int requestedFibNum;
		public long maxTime;
		
		//sets the requestedFibNum
		public FibRequestMessage(int fibNum, long maxTime)
		{
			this.requestedFibNum = fibNum;
			this.maxTime = maxTime;
		}
		
	}
	
	/**
	 * Message used to return the results from
	 */
	public static class FibReturnMessage implements Serializable{
		private static final long serialVersionUID = 1L;
		public long result;
		
		public FibReturnMessage(long r){
			this.result = r;
		}
	}
	
	//---------------------ACTOR CLASSES---------------------
	/**
	 *FibActor is an AKKA actor that accepts FibRequestMessage and
	 *returns the fib number at that index.
	 *
	 *i.e. the nth fib number
	 */
	public static class FibActor extends UntypedActor {
		@Override
		public void onReceive(Object request) throws Throwable {

			if(request instanceof FibRequestMessage){
				int requestedFibNum = ((FibRequestMessage)request).requestedFibNum;
				long maxTime = ((FibRequestMessage)request).maxTime;
				//calculate the current fib number
				FibReturnMessage result = concurrentFib(requestedFibNum, maxTime);
				//pass the results back to the sender
				getSender().tell(result, getSelf());
			}else{
				//Don't know the request
				unhandled(request);
			}
			
		}
		
		/*
		 * calculates the nth (n = fibNum) fibonacci number using child actors 
		 * instead of recursive calls
		 * */
		private FibReturnMessage concurrentFib(int fibNum, long maxTime){
			//regular base cases
			if(fibNum == 0){
				return new FibReturnMessage(0);
			}else if (fibNum <= 2){//1 or 2
				return new FibReturnMessage(1);
			}
			//here we get into the concurrent part,
			//we will create 2 child FibActors to calculate fibNum-1 and fibNum-2
			//and add them up
			else{
				//create 2 child actors
				final ActorRef firstChild = getContext().actorOf(Props.create(FibActor.class),(fibNum+"-child1"));
				final ActorRef secondChild = getContext().actorOf(Props.create(FibActor.class),(fibNum+"-child2"));
				
				//next we want to ASK them to calculate fibNum-1 and fibNum-2
				Timeout t = new Timeout(Duration.create(maxTime, TimeUnit.SECONDS));
				//async calls to ask
				Future<Object> futureChild1 = Patterns.ask(firstChild, new FibRequestMessage(fibNum-1, maxTime), t);
				Future<Object> futureChild2 = Patterns.ask(secondChild, new FibRequestMessage(fibNum-2, maxTime), t);
				FibReturnMessage res1 = null;
				FibReturnMessage res2 = null;
				//waits for the allotted time to get the results, otherwise it errors
				try {
					res1 = (FibReturnMessage)Await.result(futureChild1, t.duration());
					res2 = (FibReturnMessage)Await.result(futureChild2, t.duration());
				} catch (Exception e) {
					System.out.println("Timed out or received a Failure message");
				}
				
				return new FibReturnMessage(res1.result +  res2.result);

			}
		}
		
	}
	
	//---------------------OTHER FUNCTIONS---------------------
	
	/*
	 * Recursive version of the fibonacci calculation, used for testing/comparison 
	 */
	public static long recursiveFib(int fibNum){
		if(fibNum == 0){
			return 0;
		}else if (fibNum <= 2){//1 or 2
			return 1;
		}else{
			return recursiveFib(fibNum-1)+recursiveFib(fibNum-2);
		}
	}
	
	public static void main(String[] args) {
		int MAX_TIME = 60;
		int FIB_NUM = 20;		
		//ACTOR BASED VERSION
		// Create an actor system
		final ActorSystem fibSystem = ActorSystem.create("fibonacci");	

		// Create a FibActor actor
		final ActorRef fibActor = fibSystem.actorOf(Props.create(FibActor.class), "fibActor");

		// Create an inbox (actor in a box), allows our main function to send
		// and receive messages without being an actor itself
		final Inbox client = Inbox.create(fibSystem);
		
		
		// Ask the actor for a fibNumber message
		long conStart = System.currentTimeMillis();
		client.send(fibActor, new FibRequestMessage(FIB_NUM, MAX_TIME));
		FibReturnMessage reply = null;
		try {
			reply = (FibReturnMessage) client.receive(Duration.create(MAX_TIME, TimeUnit.SECONDS));
		}
		catch(TimeoutException e){
			System.out.println("timeout waiting for reply");
		}
		
		long conEnd = System.currentTimeMillis();
		// Print the reply received
		System.out.println("----------CONCURRENT VERSION----------");
		System.out.println("Concurrent Actor version for fib number "+FIB_NUM);
		System.out.println(reply.result);
		System.out.println("concurrent time in milliseconds: "+(conEnd-conStart));
	
		
		//close down the actor system
		fibSystem.terminate();
		
		//RECURSIVE VERSION
		System.out.println("----------RECURSIVE VERSION-----------");

		System.out.println("Recursive version for fib number "+FIB_NUM);
		long recStart = System.currentTimeMillis();
		System.out.println(recursiveFib(FIB_NUM));
		long recEnd = System.currentTimeMillis();
		System.out.println("recursive time in milliseconds: "+(recEnd-recStart));
	}
}
