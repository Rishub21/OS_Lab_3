import java.util.*;
import java.io.File;

/*
This is the main class, it has 3 purposes:

1) Sets definition of global variables such as the resource arrays
2) Parses through the input file and populates data structures accordingly
3) deploys both the naive and bankers resource allocation methods
*/
public class ResourceManager {


      static Scanner scanner;
      static List<Task> taskList; // this and the bankersTaskList holds the taskObjects that each scheduling algorithm will use
      static List<Task> bankersTaskList;
      static int [] maxResourceArr; // holds the total amount we have of each resource
      static int [] resourceArr; // holds the current amount of we have left of each resource
      static int [] taskPointers; // holds the count of which instruction we are on for each task
      static int [] releaseArr; // holds the resources we will return back in the next cycle upon a release
      static List<Instruction> waitingList; // holds the current blocked tasks, sorted by arrival time on list
      static int time; // global time variable whick tracks which cycle we are on
      static int numResources;
      static int terminatedCount;
      static Set<Instruction> removeSet; // holds the tasks that we can take off of the waiting list

      /*  The main method
        1) determines how many of each resource there are
        2) determines how many tasks there are
        3) parses through the instructions and adds each instruction to its appropriate task
        4) deploys the naive and bankers resource allocators
      */

      public static void main(String [] args){
          time = 0;
          waitingList = new ArrayList<>();
          removeSet = new HashSet<>();

          String fileName =  args[0];
          Scanner sc = new Scanner(System.in);

          // try creating a file scanner
          try{
            sc = new Scanner(new File(fileName));
          }
          catch (Exception e) {
            e.printStackTrace();
          }

          // we include two tasklists because we will have duplicates of each task object, one to be used with bankers, the other for naive
          taskList = new ArrayList<>();
          bankersTaskList = new ArrayList<>();
          int numTasks = sc.nextInt();
          numResources = sc.nextInt();

          resourceArr = new int [numResources];
          maxResourceArr = new int [numResources];

          taskPointers = new int [numTasks];

          // creating tasks from input file and adding to task list
          for(int i = 1; i <= numTasks; i ++){
            Task  t = new Task(i);
            Task bt = new Task(i);
            taskList.add(t);
            bankersTaskList.add(bt);
          }

          int curr = 0;
          // determining how many resources we have of each type
          while(curr < numResources){
            int resourceNum = sc.nextInt();
            resourceArr[curr] = resourceNum;
            maxResourceArr[curr] = resourceNum;
            curr ++;
          }

          releaseArr = new int [numResources];

          // Parsing through the instructions, and adding each one to respective task we made earlier
          while(sc.hasNext()){
            String instType = sc.next();
            int taskNumber = sc.nextInt();

            Task currTask = taskList.get(taskNumber - 1);
            Task currBankersTask = bankersTaskList.get(taskNumber - 1);
            Type instructionType = Type.valueOf(instType);

            int resourceType = sc.nextInt();
            int resourceAmount = sc.nextInt();

            // create a duplicate of the instruction object so we can use it for bankers
            Instruction i = new Instruction(instructionType, taskNumber, resourceType, resourceAmount, false);
            Instruction bI = new Instruction(instructionType, taskNumber, resourceType, resourceAmount, true);
            currTask.addInstruction(i);
            currBankersTask.addInstruction(bI);
          }

          // deploy the fifo (naive) allocator
          naive(numResources);
          System.out.println();
          System.out.println("*****FIFO*****");
          printTasks();


          // reset our global variables so Bankers can be deployed
          resetGlobals(numTasks);
          System.out.println("\n");

          // deploy the bankers allocator
          banker();
          System.out.println("*****BANKERS*****");
          printTasks();

      }

      // This method resets our global variables so bankers can run again after naive has changed them
      public static void resetGlobals(int numTasks){
        time = 0;
        terminatedCount = 0;
        removeSet.clear();
        resourceArr = maxResourceArr.clone();
        releaseArr = new int [numResources];
        taskPointers = new int [numTasks];
        taskList  = bankersTaskList;
        waitingList.clear();

      }

      // releasedResources are not available till the next cycle, so this method helps update our available resources
      public static void updateResources(){
        for(int i =  0 ; i < releaseArr.length; i ++){
          resourceArr[i] += releaseArr[i];
        }
      }

      // This method allows us to remove any tasks on the waiting lists that had their request addressed
      public static void removeWaiting(int taskNumber){
        Set<Instruction> tempSet = new HashSet<>();
        for(Instruction i : waitingList){
          if(i.taskNumber == taskNumber){
            tempSet.add(i);
          }
        }

        for(Instruction i : tempSet){
          waitingList.remove(i);
        }

      }

      // this is the bankers algorithm: it functions almost exactly like the naive except it has a few extra checks
      // to account for claims and to ensure we are always in a safe state. It first addresseds any blocked tasks, then unblocked
      public static void banker(){
        while(terminatedCount < taskList.size()){
           releaseArr = new int [numResources];

            // first address any blocked tasks
            addressWaitingBanker();
            // address any unblocked tasks
            addressNonWaitingBanker();
            // update released resources
            updateResources();
            time += 1;

            for(Instruction i : removeSet){
              waitingList.remove(i);
            }
            Collections.sort(waitingList);
        }

      }

      // this method us to determine if we are in a safe state after attempting to satisfy a particular request. It does this by seeing if we can sattisfy all
      // of this tasks' other resource needs after this hypothetical allocation in the "worst case"
      public static boolean isSafe(Task currTask){

        for(int i = 0; i < numResources; i ++){
          if(currTask.claimsArr[i] - currTask.resourceHoldings[i] > resourceArr[i]){
            return false;
          }
        }
        return true;
      }

      // This method addresses the blocked request for Bankers by seeing if we can fulfull the request via the BankerRequest method
      public static boolean addressWaitingBanker(){
        boolean addressedSomething = false;
        removeSet = new HashSet<>();
        for(Instruction i : waitingList){

          if(bankerRequest(i)){
            //System.out.println("Task " + i.taskNumber + " had its request completed off the waiting List" );
            removeSet.add(i);
            addressedSomething = true;
          }else{
            //System.out.println("Task " + i.taskNumber + " could not be completed, remains on waiting list" );
          }
        }

        return addressedSomething;
      }


      // this method addresses the non waiting instructions. It is very similar to the fifo one, but it checks if the initalizations ask for too many numResources
      // for each instruction type this method will attempt to either address or block it.
      public static boolean addressNonWaitingBanker(){
        boolean addressedSomething = false;
        for(int i = 0 ; i < taskPointers.length; i ++){
          int pointerIndex = taskPointers[i];
          Task currTask = taskList.get(i);

          if(currTask.terminateTime == - 1 && (currTask.isAborted == false) && Collections.disjoint(waitingList, currTask.instructionList)){

            Instruction currInstruction = currTask.instructionList.get(pointerIndex);
            Type instructionType = currInstruction.instructionType;
            int resourceType = currInstruction.resourceType;

            if(instructionType == Type.initiate){
              if(currInstruction.claim > maxResourceArr[currInstruction.resourceType]){
                System.out.println("Banker aborts task " + currTask.taskNumber+ " before run begins: claim for resource " + resourceType + " (" + currInstruction.claim + ") " + " number of units present " + " (" + maxResourceArr[currInstruction.resourceType] + ") ");
                currTask.isAborted = true;
                terminatedCount += 1;
              }else{
                currTask.startTime = time;
                addressedSomething = true;
              }

            }else if(instructionType == Type.request){
              if(bankerRequest(currInstruction)){
                addressedSomething = true;
              }else{
              //  System.out.println("Task " + currTask.taskNumber + " could not be completed");
              }
            }// when it is time to add the waitingInstructions what you should do is something along the lines of
            else if(instructionType == Type.compute){
              int numberCycles = currInstruction.numberCycles;
              if(currTask.computeTime == 0){
                currTask.computeTime = currInstruction.numberCycles;
              }
              currTask.computeTime -= 1;

              //System.out.println("Task " + currTask.taskNumber + " computes " + (currInstruction.numberCycles - currTask.computeTime));

              addressedSomething = true;
            }else if(instructionType == Type.release){
              int amountReleased = currInstruction.resourceAmount;
              release(resourceType, amountReleased, currTask);
              //System.out.println("Task " + currTask.taskNumber + " released its resources");
              addressedSomething = true;
            }else{ // if its terminate
              currTask.terminateTime = time;
              //System.out.println("Task " + currTask.taskNumber + " terminates at time t = " + time);
              terminatedCount ++;
              releaseAll(currTask);
              addressedSomething = true;
            }
            if(currTask.computeTime == 0){
              taskPointers[i] ++;
            }
          }

        }
        return addressedSomething;
      }

      // this method determines if a request can be made. It determines if we have enough resources, if it does not exceed claim and if the request puts us in a safe state by calling the isSafe method defined above
      public static boolean bankerRequest(Instruction currInstruction){
        Task currTask = taskList.get(currInstruction.taskNumber - 1);
        int resourceType = currInstruction.resourceType;
        int amountRequested = currInstruction.resourceAmount;
        if(currTask.claimsArr[resourceType - 1] < currTask.resourceHoldings[resourceType - 1] + amountRequested ){
          System.out.println("During Cycle " + time + "-" + (time + 1) + " of Banker's algorithm, Task " + currTask.taskNumber + " request exceeds its claim; aborted ");
          for(int j = 0 ; j < currTask.resourceHoldings.length; j ++){
            System.out.println(currTask.resourceHoldings[j] + " units of resource " + (j+ 1) +" available next cycle");
          }
          currTask.isAborted = true;
          terminatedCount ++;
          releaseAll(currTask);
          return false;
        }

        if(resourceArr[resourceType - 1] >= amountRequested){
          currTask.resourceHoldings[resourceType - 1] += amountRequested;
          resourceArr[resourceType - 1] -= amountRequested;

          if(isSafe(currTask)){
            return true;
          }else{
            currTask.resourceHoldings[resourceType - 1] -= amountRequested;
            resourceArr[resourceType - 1] += amountRequested;

            if(!waitingList.contains(currInstruction)){
              currInstruction.arrivalTime = time;
              waitingList.add(currInstruction);
            }
            currTask.waitingCount += 1;

            return false;
          }
        }else{

          if(!waitingList.contains(currInstruction)){
            currInstruction.arrivalTime = time;
            waitingList.add(currInstruction);
          }
          currTask.waitingCount += 1;
        }
        return false;
      }

      // this is how our fifo allocator is deployed, by first addressing the blocked request and then the non blocked requests.
      //  it will continue to abort tasks in the wihle loop as long as it does not have thhe resources it needs
      public static void naive(int numResources){
          while(terminatedCount < taskList.size()){

              releaseArr = new int [numResources];
              boolean wait = addressWaiting();
              boolean notWait = addressNonWaiting();

              while((wait == false && notWait == false) && !checkResources() && terminatedCount < taskList.size() ){
                for(Task t : taskList){
                  if(t.terminateTime == -1 && (!t.isAborted)){ // not terminated yet
                    releaseAll(t);

                    t.isAborted = true;
                    terminatedCount ++;
                    removeWaiting(t.taskNumber);

                    break; // break out of forloop
                  }
                }
                updateResources();
              }

              updateResources();
              time += 1;

              for(Instruction i : removeSet){
                waitingList.remove(i);
              }
              Collections.sort(waitingList);
          }
      }

      public static boolean addressWaiting(){
        boolean addressedSomething = false;
        removeSet = new HashSet<>();
        for(Instruction i : waitingList){
          if(request(i)){
            //System.out.println("Task " + i.taskNumber + " had its request completed off the waiting List" );

            removeSet.add(i);
            addressedSomething = true;
          }else{
            //System.out.println("Task " + i.taskNumber + " could not be completed, remains on waiting list" );
          }
        }

        return addressedSomething;
      }

      public static boolean addressNonWaiting(){
        boolean addressedSomething = false;
        for(int i = 0 ; i < taskPointers.length; i ++){
          int pointerIndex = taskPointers[i];
          Task currTask = taskList.get(i);

          if(currTask.terminateTime == - 1 && (currTask.isAborted == false) && Collections.disjoint(waitingList, currTask.instructionList)){
            // see if we can allocate resources


            Instruction currInstruction = currTask.instructionList.get(pointerIndex);
            Type instructionType = currInstruction.instructionType;
            int resourceType = currInstruction.resourceType;

            if(instructionType == Type.initiate){
              currTask.startTime = time;
              addressedSomething = true;
              //System.out.println("Task " + currTask.taskNumber + " was initiated");
            }else if(instructionType == Type.request){
              if(request(currInstruction)){
                addressedSomething = true;
                //System.out.println("Task " + currTask.taskNumber + " had its request completed");
              }else{
                //System.out.println("Task " + currTask.taskNumber + " could not be completed");
              }
            }// when it is time to add the waitingInstructions what you should do is something along the lines of
            else if(instructionType == Type.compute){
              int numberCycles = currInstruction.numberCycles;
              if(currTask.computeTime == 0){
                currTask.computeTime = currInstruction.numberCycles;
              }
              currTask.computeTime -= 1;

              //System.out.println("Task " + currTask.taskNumber + " computes " + (currInstruction.numberCycles - currTask.computeTime));

              addressedSomething = true;
            }else if(instructionType == Type.release){
              int amountReleased = currInstruction.resourceAmount;
              release(resourceType, amountReleased, currTask);
              //System.out.println("Task " + currTask.taskNumber + " released its resources");
              addressedSomething = true;
            }else{ // if its terminate
              currTask.terminateTime = time;
            //  System.out.println("Task " + currTask.taskNumber + " terminates at time t = " + time);
              terminatedCount ++;
              releaseAll(currTask);
              addressedSomething = true;
            }
            if(currTask.computeTime == 0){
              taskPointers[i] ++;
            }
          }

        }
        return addressedSomething;
      }
      // this helper method releases all the resources of an aborted or terminated tasks
      public static void releaseAll(Task currTask){
        for(int j = 0 ; j < currTask.resourceHoldings.length; j ++){
          release(j + 1, currTask.resourceHoldings[j], currTask);
        }
      }

      // this is how the fifo algorithm conducts a request, if there are enoughh resources it will satify, otehrwise it will block
      public static boolean request(Instruction currInstruction){
        Task currTask = taskList.get(currInstruction.taskNumber - 1);
        int resourceType = currInstruction.resourceType;
        int amountRequested = currInstruction.resourceAmount;
        if(resourceArr[resourceType - 1] >= amountRequested){
          currTask.resourceHoldings[resourceType - 1] += amountRequested;
          resourceArr[resourceType - 1] -= amountRequested;
          return true;
        }else{
          if(!waitingList.contains(currInstruction)){
            currInstruction.arrivalTime = time;
            waitingList.add(currInstruction);
          }
          currTask.waitingCount += 1;
        }
        return false;
      }

// this returns an individual resource of a task to our holdings array
      public static void release(int resourceType, int amountReleased, Task currTask){
        currTask.resourceHoldings[resourceType - 1] -= amountReleased;
        releaseArr[resourceType - 1] += amountReleased;
      }

// this determines if we have enough resources to fulfill the next instruction. if not thhe fifo algorithm will continue to abort tasks
      public static boolean checkResources(){
        for(Instruction i : waitingList){
          if(resourceArr[i.resourceType - 1] >= i.resourceAmount){
            return true;
          }
        }
        for(int i = 0 ; i < taskPointers.length; i ++){
          int pointerIndex = taskPointers[i];
          Task currTask = taskList.get(i);
          if(currTask.terminateTime == - 1 && (currTask.isAborted == false) && Collections.disjoint(waitingList, currTask.instructionList)){
            Instruction currInstruction = currTask.instructionList.get(pointerIndex);
            Type instructionType = currInstruction.instructionType;
            int resourceType = currInstruction.resourceType;
            if(instructionType != Type.request){
              return true;
            }else{
              if(resourceArr[currInstruction.resourceType - 1] >= currInstruction.resourceAmount){
                return true;
              }
            }
          }

      }
      return false;
    }

    // the method we use to print the ending and waiting time for each task
    public static void printTasks(){
      int totalTime = 0;
      int totalWait = 0;
      for(Task t : taskList){

        if(t.isAborted){
          System.out.println("TASK " + t.taskNumber + " aborted"  );
        }else{
          System.out.println("TASK " + t.taskNumber + " " + t.terminateTime + " " + t.waitingCount  + " %" + 100 * ((float)t.waitingCount / t.terminateTime));
          totalTime += t.terminateTime;
          totalWait += t.waitingCount;
        }
      }
      System.out.println("TOTAL TIME:  " + totalTime + " TOTAL WAIT: " + totalWait + " %" + 100 *((float)totalWait / totalTime));

    }

}
// a simple object which represents a task,
class Task {

  List<Instruction> instructionList; // all the instructions that are associated with this task, in the order they were presented
  int taskNumber;
  int startTime;
  int terminateTime = -1;
  int useTime;
  int [] resourceHoldings; // the amount of each resource that a task currently holds
  boolean isAborted = false;
  int computeTime;
  int waitingCount;
  int [] claimsArr; // the claim for each resource that a task has

  public Task(int taskNumber){
    instructionList = new ArrayList<>();
    this.taskNumber = taskNumber;
    this.resourceHoldings = new int [ResourceManager.numResources];
    this.claimsArr = new int [ResourceManager.numResources];
  }

  public void addInstruction(Instruction i){
    this.instructionList.add(i);
  }

  public void printTask(){
      for(Instruction i : instructionList){
        i.printInstruction();
      }

  }


}
// a simple object which represents an instruction, each instruction belongs to a specific task and has information about what type of instruction and the number and types of resources it is involved with
class Instruction implements Comparable<Instruction> {
  Type instructionType;
  int taskNumber;
  int resourceType;
  int resourceAmount;
  int numberCycles;
  int claim;
  int arrivalTime;

  public Instruction(Type instructionType, int taskNumber, int resourceType, int resourceAmount, boolean banker){
    this.instructionType = instructionType;
    this.taskNumber = taskNumber;


    if(instructionType == Type.compute){
      this.numberCycles = resourceType;
    }else if(instructionType == Type.release || instructionType == Type.request){
      this.resourceType = resourceType;
      this.resourceAmount = resourceAmount;
    }else if(instructionType == Type.initiate){
      this.claim = resourceAmount;
      if(!banker){
        (ResourceManager.taskList.get(taskNumber - 1)).claimsArr[resourceType - 1] = resourceAmount;
      }else{
        (ResourceManager.bankersTaskList.get(taskNumber - 1)).claimsArr[resourceType - 1] = resourceAmount;
      }
    }else{
      // terminate
    }

  }

  public void printInstruction(){
    System.out.println(instructionType);
  }

  public int compareTo(Instruction other){
    return this.arrivalTime - other.arrivalTime;
  }

}

// each instruction can only be of these types
enum Type{
  initiate,request, release,compute,terminate;
}
