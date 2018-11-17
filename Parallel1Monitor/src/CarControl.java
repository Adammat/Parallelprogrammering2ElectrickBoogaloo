//Prototype implementation of Car Control
//Mandatory assignment
//Course 02158 Concurrent Programming, DTU, Fall 2018

//Hans Henrik Lovengreen      Oct 8, 2018


import java.awt.Color;

class Gate {

    Semaphore g = new Semaphore(0);
    Semaphore e = new Semaphore(1);
    boolean isopen = false;

    public void pass() throws InterruptedException {
        g.P(); 
        g.V();
    }

    public void open() {
        try { e.P(); } catch (InterruptedException e) {}
        if (!isopen) { g.V();  isopen = true; }
        e.V();
    }

    public void close() {
        try { e.P(); } catch (InterruptedException e) {}
        if (isopen) { 
            try { g.P(); } catch (InterruptedException e) {}
            isopen = false;
        }
        e.V();
    }

}

class Conductor extends Thread {

    final static int steps = 10;
    static final int upperBarRow = 4;
    static final int lowerBarRow = 5;
    boolean INFFLAG = false;
    double basespeed = 6.0;          // Tiles per second
    double variation =  50;          // Percentage of base speed

    CarDisplayI cd;                  // GUI part
    Semaphore[][] semTiles;			 // anti crash semaphores
    Boolean[][] alleyTiles;			 //Grid for the alley

    int no;                          // Car number
    Pos startpos;                    // Start position (provided by GUI)
    Pos barpos;                      // Barrier position (provided by GUI)
    Color col;                       // Car  color
    Gate mygate;                     // Gate at start position

    Pos curpos;                      // Current position 
    Pos newpos;                      // New position to go to

    Semaphore disabledLock;				//New tile semaphore
    Boolean disabled;
    
    Barrier bar;
    Alley alley = new Alley();
    Pos alleyEnter;
    Pos alleyLeave;
    
    CarI car;
    
    public synchronized void printSemMap() {
    	String positions = "";
    	for(int i =0;i < this.semTiles.length;i++) {
    		for(int j =0;j < this.semTiles[i].length;j++) {
        		if(semTiles[i][j].toString().equals("1")){
        			System.out.print("[ ]");
        		}
        		else {
        			System.out.print("[X]");
        			positions+="("+i+","+j+")";
        		}
        	}
    		System.out.print("\n");
    	}
    	System.out.println("Positions: "+positions);
    	System.out.println("[----------------------------------]");
    }
    
    
    public Conductor(int no, CarDisplayI cd, Gate g, Semaphore[][] semTiles, Alley alley, Barrier bar, Pos alleyEnter, Pos alleyLeave) {

    	
    	this.alleyEnter = alleyEnter;
        this.alleyLeave = alleyLeave;
        this.alley = alley;
        this.semTiles = semTiles;
        this.no = no;
        this.cd = cd;
        this.bar = bar;
        mygate = g;
        startpos = cd.getStartPos(no);
        barpos   = cd.getBarrierPos(no);  // For later use
        
        disabledLock = new Semaphore(0);
        this.disabled = false;
        
        col = chooseColor();

        // special settings for car no. 0
        if (no == 0 || INFFLAG) {
            basespeed = -1.0;  
            variation = 0; 
        }
        
    }

    
    
    public synchronized void setSpeed(double speed) { 
        basespeed = speed;
    }

    public synchronized void setVariation(int var) { 
        if (no != 0 && 0 <= var && var <= 100) {
            variation = var;
        }
        else
            cd.println("Illegal variation settings");
    }

    synchronized double chooseSpeed() { 
        double factor = (1.0D+(Math.random()-0.5D)*2*variation/100);
        return factor*basespeed;
    }

    Color chooseColor() { 
        return Color.blue; // You can get any color, as longs as it's blue 
    }

    Pos nextPos(Pos pos) {
        // Get my track from display
        return cd.nextPos(no,pos);
    }

    boolean atGate(Pos pos) {
        return pos.equals(startpos);
    }

	private boolean atBarrier(Pos pos) {
		//Checks if current car is at the barrier
		return pos.equals(cd.getBarrierPos(no));
	}
    
    public void run() {
        try {
        	
            car = cd.newCar(no, col, startpos);
            curpos = startpos;
            semTiles[startpos.row][startpos.col].P();
            cd.register(car);
            
            
            //print speed af bil mens den kører
            while (true) {  
            	
                if (atGate(curpos)) { 
                    mygate.pass();    
                    car.setSpeed(chooseSpeed());//Has race condition(?)
                }
                
                if (atBarrier(curpos)) {
                	bar.sync(no);
                }	
                
                newpos = nextPos(curpos);
                
              //Check if we are entering the alley
                if(newpos.equals(alleyEnter)){
                	alley.enter(no);
                } 
               
               driveAtomic(this);
               
               
                
                if(newpos.equals(alleyLeave)){
                	alley.leave(no);
                }
                curpos = newpos;

            }

        } catch (Exception e) {
            cd.println("Exception in Car no. " + no + ". Current speed: " + basespeed);
            System.err.println("Exception in Car no. " + no + ":" + e);
            e.printStackTrace();
        }
    }

private synchronized void driveAtomic(Conductor c) {
     try {
		semTiles[newpos.row][newpos.col].P();
		
	} catch (InterruptedException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
  
     try {
		car.driveTo(newpos);
		semTiles[curpos.row][curpos.col].V();
	} catch (InterruptedException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
     
     //Frees up old position
     
     
     
  
}

}

public class CarControl implements CarControlI{

	Barrier bar;
    CarDisplayI cd;           // Reference to GUI
    Conductor[] conductor;    // Car controllers
    Gate[] gate;              // Gates
    Semaphore[][] semTiles;
    Boolean[][] alleyTiles;
    Alley alley;
    static final int ROWS = 11;
    static final int COLS = 12;
	

    Pos[] alleyEnter;
    Pos[] alleyLeave;
    
    Semaphore tempSem;
    
    public CarControl(CarDisplayI cd) {
        this.cd = cd;
        conductor = new Conductor[9];
        gate = new Gate[9];
        semTiles = new Semaphore[ROWS][COLS];
        bar = new Barrier();
        alley = new Alley();
        
        //Setup tiles
        for(int i = 0; i < ROWS; i++){
        	for (int j = 0; j<COLS; j++){
        		semTiles[i][j] = new Semaphore(1);
        	}
        }
        
        Pos tempAlleyEnter;
        Pos tempAlleyLeave;
        for (int no = 0; no < 9; no++) {
        	//Setup alley enter and leave points
        	if(no<3){// Car 1, 2 and 0 (0 is irrelevant)
        		tempAlleyEnter = new Pos(ROWS-3,0);
        		tempAlleyLeave = new Pos(1,1);
        	} else if (no < 5){// Car 3 and 4
        		tempAlleyEnter = new Pos(ROWS-2,2);
        		tempAlleyLeave = new Pos(1,1);
            } else { // Car 5-8
            	tempAlleyEnter = new Pos(1,0);
            	tempAlleyLeave = new Pos(ROWS-1, 2);
            }
        	
            gate[no] = new Gate();
            conductor[no] = new Conductor(no,cd,gate[no], semTiles, alley, bar, tempAlleyEnter, tempAlleyLeave);
            conductor[no].setName("Conductor-" + no);
            conductor[no].start();
        } 
    }

    public void startCar(int no) {
        gate[no].open();
    }

    public void stopCar(int no) {
        gate[no].close();
    }

    public void barrierOn() { 
        bar.on();
    }

    public void barrierOff() {
    	bar.off();
    	}

    public void barrierSet(int k) { 
        
        	try {
				bar.barrierSet(k);
			} catch (IndexOutOfBoundsException e) {
				// Prints the error to the console
				cd.println(e.toString());
			}
        	
        //try { Thread.sleep(3000); } catch (InterruptedException e) { }
    }

    public synchronized void removeCar(int no) { 
    	Conductor cond = conductor[no];
    	if(!cond.disabled){
    		
    		cond.disabled = true;
    		
	        //Frees up old position
    		cd.deregister(cond.car);
    		Pos next = cond.nextPos(cond.curpos);
	        cond.semTiles[cond.curpos.row][cond.curpos.col].V();
	        cond.semTiles[next.row][next.col].V();
	        alley.leave(no);
	        cd.println("Remove Car no: " + no);
	        cd.println("Disabled: "+ cond.disabled+", Semaphore release position: ("+cond.curpos.row+","+cond.curpos.col+") , Position: "+cond.curpos+", Destination: "+cond.startpos);
	    } else {
	    	cd.println("Car already removed");
	    }
    }

    public synchronized void restoreCar(int no) { 
    	
    	
    	
    	if(conductor[no].disabled){
    		conductor[no] = new Conductor(no,cd,gate[no], semTiles, alley, bar, conductor[no].alleyEnter, conductor[no].alleyLeave);
            conductor[no].setName("Conductor-" + no);
            conductor[no].start();
    		
	    	
        
        } else {
        	cd.println("Car alredy exist");
        	//conductor[no].printSemMap();
        }
    }

    /* Speed settings for testing purposes */

    public void setSpeed(int no, double speed) { 
        conductor[no].setSpeed(speed);
    }

    public void setVariation(int no, int var) { 
        conductor[no].setVariation(var);
    }
 
}

class Alley{

	final int MAX_NO_CARS = 8;
	int carCounter = 0;
	int waitCars = 0;
	Boolean curDir = false; //False = up, True = down
	boolean[] fairnessGuard = new boolean[8];
	Boolean upperWait = false;
	Boolean lowerWait = false;
	
	
	public synchronized void enter(int no) throws InterruptedException{
		/* (no-1) mod 4*/
		
		Boolean tempBol = no>4 ? lowerWait : upperWait;
		
		while((carCounter != 0 && no>4 != curDir)|| (fairnessGuard[no-1] && (tempBol) ) ){
			
			if(no>4){
				upperWait = true;
			} else {
				lowerWait = true;
			}
			
			this.wait();
			
			if(no>4){
				upperWait = false;
			} else {
				lowerWait = false;
			}
			
			tempBol = no>4 ? lowerWait : upperWait;
		}
		fairnessGuard[no-1] = true;
		curDir = no>4; //no>4 is the direction of the car with the given number 
		carCounter++;
		
		
	}
	
	public synchronized void leave(int no){
		if(no>4 == curDir){
			//Notes that the car has left the alley
			carCounter--;
			if (carCounter <= 0){
				//In case that this is the last car, release the waiting cars
				carCounter = 0;
				int offSet = no>4 ? 0 : 4;
				
				for(int i = offSet; i < offSet+4; i++){
					fairnessGuard[i] = false;
				}
				this.notifyAll();
				
			}
		}//*/
		
	}
	
	
}




class Barrier {
	
	final int MAX_NO_CARS = 9;
	
	Boolean flag = false;
	int counter = 0;
	int threshold = MAX_NO_CARS;
	int newThreshold = threshold;
	
	
	public synchronized void sync(int no) throws InterruptedException {
		if(flag) {
			//Incremets the number of cars currently waiting
			counter++;
			if (counter >= threshold) {
				//If the number of waiting cars exceeds the threshold free the other cars
				freeCars();
				} else {
				//Otherwise wait
				
				this.wait();
			}
			
		}
	}
	
	
	public synchronized void on(){
		flag = true;
	}
	
	public synchronized void off(){
		flag = false;
		freeCars();
	}
	
	//Setting the threshold for the amount of cars that the barrier keeps back
	public synchronized void barrierSet(int k) throws IndexOutOfBoundsException{
		if(k <= MAX_NO_CARS){
			//Sets the new threshold
			newThreshold = k;
			//Checks if there currently is waiting more than the new threshold (This updates the threshold)
			if (counter >= newThreshold){
				freeCars();
			//If the new threshold is less than the old, or there are no cars, update the threshold immediately  
			} else if(threshold > newThreshold || counter == 0){ 
				threshold = newThreshold;
			}
		}else{
			throw new IndexOutOfBoundsException("Threshold greater than Max number of cars"); 
		}
	}
	
	private void freeCars(){
		this.notifyAll();
		counter = 0;
		threshold = newThreshold;
	}
		
}








