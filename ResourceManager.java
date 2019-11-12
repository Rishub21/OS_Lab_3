import java.util.*;
import java.io.File;

public class ResourceManager {

      static Scanner scanner;
      static List<Task> taskList;
      static List<Task> bankersTaskList;
      static int [] maxResourceArr;
      static int [] resourceArr;
      static int [] bankersResourceArr;
      static int [] taskPointers;
      static int [] bankersTaskPointers;
      static int [] releaseArr;
      static List<Instruction> waitingList;
      static int time;
      static boolean cont;
      static int numResources;
      static int terminatedCount;
      static Set<Instruction> removeSet;


      public static void main(String [] args){
          time = 0;
          waitingList = new ArrayList<>();
          String fileName =  args[0];
          Scanner sc = new Scanner(System.in);
          try{
            sc = new Scanner(new File(fileName));
          }
          catch (Exception e) {
            e.printStackTrace();
          }
          taskList = new ArrayList<>();
          int numTasks = sc.nextInt();
          numResources = sc.nextInt();

          resourceArr = new int [numResources];
          maxResourceArr = new int [numResources];

          taskPointers = new int [numTasks];

          for(int i = 1; i <= numTasks; i ++){
            Task  t = new Task(i);
            taskList.add(t);
          }

          int curr = 0;
          while(curr < numResources){
            int resourceNum = sc.nextInt();
            resourceArr[curr] = resourceNum;
            maxResourceArr[curr] = resourceNum;
            curr ++;
          }

          releaseArr = new int [numResources];

          while(sc.hasNext()){
            String instType = sc.next();
            int taskNumber = sc.nextInt();

            Task currTask = taskList.get(taskNumber - 1);
            Type instructionType = Type.valueOf(instType);

            int resourceType = sc.nextInt();
            int resourceAmount = sc.nextInt();

            Instruction i = new Instruction(instructionType, taskNumber, resourceType, resourceAmount);
            currTask.addInstruction(i);
          }
          // naive(numResources);
          // for(Task t : taskList){
          //   t.printTask();
          // }
          // System.out.println("************************************");
          // naivePrint();


          banker();
          System.out.println("************************************");
          naivePrint();

      }

      public static void updateResources(){
        for(int i =  0 ; i < releaseArr.length; i ++){
          resourceArr[i] += releaseArr[i];
        }
      }

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
      public static void banker(){
        while(terminatedCount < taskList.size()){
           releaseArr = new int [numResources];

            System.out.println("TIME : " + time);
            System.out.println("CONTENTS: " + resourceArr[0]);

            boolean wait = addressWaitingBanker();
            boolean notWait = addressNonWaitingBanker();

            updateResources();
            time += 1;

            for(Instruction i : removeSet){
              waitingList.remove(i);
            }
            Collections.sort(waitingList);
        }

      }
      public static boolean resourceCheck(Task currTask){
        // task.claims  -= task.resourceholdings  <= resoruceholdings

        for(int i = 0; i < numResources; i ++){
          if(currTask.claimsArr[i] - currTask.resourceHoldings[i] > resourceArr[i]){
            return false;
          }
        }
        return true;
      }

      public static boolean addressWaitingBanker(){
        boolean addressedSomething = false;
        removeSet = new HashSet<>();
        for(Instruction i : waitingList){
          //System.out.println("YOOOO" + resourceArr[i.resourceType - 1]);
          // if( i.taskNumber == 1){
          //   System.out.println("YOOOO: " + time +  + i.resourceAmount + " " + i.resourceType + " " + resourceArr[i.resourceType - 1]);
          // }
          if(bankerRequest(i)){
            System.out.println("Task " + i.taskNumber + " had its request completed off the waiting List" );

            removeSet.add(i);
            addressedSomething = true;
          }else{
            System.out.println("Task " + i.taskNumber + " could not be completed, remains on waiting list" );
          }
        }

        return addressedSomething;
      }


      public static boolean addressNonWaitingBanker(){
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
              if(currInstruction.claim > maxResourceArr[currInstruction.resourceType]){
                currTask.isAborted = true;
                terminatedCount += 1;
              }else{
                currTask.startTime = time;
                addressedSomething = true;
                System.out.println("Task " + currTask.taskNumber + " was initiated");
              }

            }else if(instructionType == Type.request){
              if(bankerRequest(currInstruction)){
                addressedSomething = true;
                System.out.println("Task " + currTask.taskNumber + " had its request completed");
              }else{
                System.out.println("Task " + currTask.taskNumber + " could not be completed");
              }
            }// when it is time to add the waitingInstructions what you should do is something along the lines of
            else if(instructionType == Type.compute){
              int numberCycles = currInstruction.numberCycles;
              if(currTask.computeTime == 0){
                currTask.computeTime = currInstruction.numberCycles;
              }
              currTask.computeTime -= 1;

              System.out.println("Task " + currTask.taskNumber + " computes " + (currInstruction.numberCycles - currTask.computeTime));

              addressedSomething = true;
            }else if(instructionType == Type.release){
              int amountReleased = currInstruction.resourceAmount;
              release(resourceType, amountReleased, currTask);
              System.out.println("Task " + currTask.taskNumber + " released its resources");
              addressedSomething = true;
            }else{ // if its terminate
              currTask.terminateTime = time;
              System.out.println("Task " + currTask.taskNumber + " terminates at time t = " + time);
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

      public static boolean bankerRequest(Instruction currInstruction){
        Task currTask = taskList.get(currInstruction.taskNumber - 1);
        int resourceType = currInstruction.resourceType;
        int amountRequested = currInstruction.resourceAmount;
        if(currTask.claimsArr[resourceType - 1] < currTask.resourceHoldings[resourceType - 1] + amountRequested ){
          currTask.isAborted = true;
          terminatedCount ++;
          releaseAll(currTask);
          return false;
        }

        if(resourceArr[resourceType - 1] >= amountRequested){
          currTask.resourceHoldings[resourceType - 1] += amountRequested;
          resourceArr[resourceType - 1] -= amountRequested;
          System.out.println("CONTENTS** " +       resourceArr[resourceType - 1] );

          if(resourceCheck(currTask)){
            return true;
          }else{
            currTask.resourceHoldings[resourceType - 1] -= amountRequested;
            resourceArr[resourceType - 1] += amountRequested;
            System.out.println("CONTENTS** " +       resourceArr[resourceType - 1] );

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

      public static void naive(int numResources){
          while(terminatedCount < taskList.size()){

              releaseArr = new int [numResources];
              System.out.println("TIME : " + time);

              boolean wait = addressWaiting();
              boolean notWait = addressNonWaiting();

              while((wait == false && notWait == false) && !checkResources() && terminatedCount < taskList.size() ){
                for(Task t : taskList){
                  if(t.terminateTime == -1 && (!t.isAborted)){ // not terminated yet
                    releaseAll(t);

                    t.isAborted = true;
                    terminatedCount ++;
                    System.out.println("Task " + t.taskNumber + " is aborted");

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
            System.out.println("Task " + i.taskNumber + " had its request completed off the waiting List" );

            removeSet.add(i);
            addressedSomething = true;
          }else{
            System.out.println("Task " + i.taskNumber + " could not be completed, remains on waiting list" );
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
              System.out.println("Task " + currTask.taskNumber + " was initiated");
            }else if(instructionType == Type.request){
              if(request(currInstruction)){
                addressedSomething = true;
                System.out.println("Task " + currTask.taskNumber + " had its request completed");
              }else{
                System.out.println("Task " + currTask.taskNumber + " could not be completed");
              }
            }// when it is time to add the waitingInstructions what you should do is something along the lines of
            else if(instructionType == Type.compute){
              int numberCycles = currInstruction.numberCycles;
              if(currTask.computeTime == 0){
                currTask.computeTime = currInstruction.numberCycles;
              }
              currTask.computeTime -= 1;

              System.out.println("Task " + currTask.taskNumber + " computes " + (currInstruction.numberCycles - currTask.computeTime));

              addressedSomething = true;
            }else if(instructionType == Type.release){
              int amountReleased = currInstruction.resourceAmount;
              release(resourceType, amountReleased, currTask);
              System.out.println("Task " + currTask.taskNumber + " released its resources");
              addressedSomething = true;
            }else{ // if its terminate
              currTask.terminateTime = time;
              System.out.println("Task " + currTask.taskNumber + " terminates at time t = " + time);
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
      public static void releaseAll(Task currTask){
        for(int j = 0 ; j < currTask.resourceHoldings.length; j ++){
          release(j + 1, currTask.resourceHoldings[j], currTask);
        }
      }
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

      public static void release(int resourceType, int amountReleased, Task currTask){
        currTask.resourceHoldings[resourceType - 1] -= amountReleased;
        releaseArr[resourceType - 1] += amountReleased;
      }

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

    public static void naivePrint(){
      for(Task t : taskList){

        if(t.isAborted){
          System.out.println("TASK " + t.taskNumber + " aborted"  );
        }else{
          System.out.println("TASK " + t.taskNumber + " " + t.terminateTime + " " + t.waitingCount );
        }
      }

    }

}
// any instruction except terminate will cause the task's useTime to increment .
class Task {

  List<Instruction> instructionList;
  int taskNumber;
  int startTime;
  int terminateTime = -1;
  int useTime;
  int [] resourceHoldings;
  boolean isAborted = false;
  int computeTime;
  int waitingCount;
  int [] claimsArr;

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
      // System.out.println("TASK NUMBER: " + taskNumber);
      // System.out.println("Termination " + terminateTime);
      // System.out.println("INSTRUCTION LIST");
      for(Instruction i : instructionList){
        i.printInstruction();
      }

  }


}

class Instruction implements Comparable<Instruction> {
  Type instructionType;
  int taskNumber;
  int resourceType;
  int resourceAmount;
  int numberCycles;
  int claim;
  int arrivalTime;

  public Instruction(Type instructionType, int taskNumber, int resourceType, int resourceAmount){
    this.instructionType = instructionType;
    this.taskNumber = taskNumber;


    if(instructionType == Type.compute){
      this.numberCycles = resourceType;
    }else if(instructionType == Type.release || instructionType == Type.request){
      this.resourceType = resourceType;
      this.resourceAmount = resourceAmount;
    }else if(instructionType == Type.initiate){
      this.claim = resourceAmount;
      (ResourceManager.taskList.get(taskNumber - 1)).claimsArr[resourceType - 1] = resourceAmount;
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

enum Type{
  initiate,request, release,compute,terminate;
}


// set each resource to have a count ,

// go through all the instructions, keep track if you have enough to fulfil
