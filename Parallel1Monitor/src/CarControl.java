//Prototype implementation of Car Control
//Mandatory assignment
//Course 02158 Concurrent Programming, DTU, Fall 2018

//Hans Henrik Lovengreen      Oct 8, 2018

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;

class Gate {

	Semaphore g = new Semaphore(0);
	Semaphore e = new Semaphore(1);
	boolean isopen = false;

	public void pass() throws InterruptedException {
		g.P();
		g.V();
	}

	public void open() {
		try {
			e.P();
		} catch (InterruptedException e) {
		}
		if (!isopen) {
			g.V();
			isopen = true;
		}
		e.V();
	}

	public void close() {
		try {
			e.P();
		} catch (InterruptedException e) {
		}
		if (isopen) {
			try {
				g.P();
			} catch (InterruptedException e) {
			}
			isopen = false;
		}
		e.V();
	}

}

class Conductor extends Thread {

	final static int steps = 10;
	static final int upperBarRow = 4;
	static final int lowerBarRow = 5;
	boolean INFFLAG = true;
	double basespeed = 6.0; // Tiles per second
	double variation = 50; // Percentage of base speed
	ArrayList<Pos> claimedTiles = new ArrayList<Pos>(); //Ledger of claimed semaphores
	CarDisplayI cd; // GUI part
	Semaphore[][] semTiles; // anti crash semaphores

	int no; // Car number
	Pos startpos; // Start position (provided by GUI)
	Pos barpos; // Barrier position (provided by GUI)
	Color col; // Car color
	Gate mygate; // Gate at start position

	Pos curpos; // Current position
	Pos newpos; // New position to go to
	Boolean disabled; //Disabled flag

	Barrier bar;
	Alley alley = new Alley();
	Pos alleyEnter; //Entry point of alley
	Pos alleyLeave; //Exit point of alley
	CarI car;

	public synchronized void printTrackDebug() {
		String positions = "";
		for (int i = 0; i < this.semTiles.length; i++) {
			for (int j = 0; j < this.semTiles[i].length; j++) {
				System.out.print("[" + semTiles[i][j].toString() + "]");

			}
			System.out.print("\n");
		}
		System.out.println("Positions: " + positions);
		System.out.println("[----------------------------------]");
		System.out.println("Ally Status:[U: " + alley.upWaiting + ", L: " + alley.downWaiting + "] ");
		System.out.println("WaitArray: " + Arrays.toString(alley.waitForOpposing) + "]");
		System.out.println("DisabledArray: " + Arrays.toString(alley.getCarDisabled()) + "]");
		System.out.println("Current Direction:(False= up, True= down) " + alley.curDir + "]");
		System.out.println("AlleyCars: " + alley.currentCars + "]");

		System.out.println("[----------------------------------]");
	}

	public Conductor(int no, CarDisplayI cd, Gate g, Semaphore[][] semTiles, Alley alley, Barrier bar, Pos alleyEnter, Pos alleyLeave) {
		this.alleyEnter = alleyEnter;
		this.alleyLeave = alleyLeave;
		this.alley = alley;
		this.semTiles = semTiles;
		this.no = no; //Car number
		this.cd = cd; //Car Display Object
		this.bar = bar; //Barrier
		mygate = g; //Gate belonging to conductor
		startpos = cd.getStartPos(no);
		barpos = cd.getBarrierPos(no); // For later use
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
		} else
			cd.println("Illegal variation settings");
	}

	synchronized double chooseSpeed() {
		double factor = (1.0D + (Math.random() - 0.5D) * 2 * variation / 100);
		return factor * basespeed;
	}

	Color chooseColor() {
		return Color.blue; // You can get any color, as longs as it's blue.
	}					   // We like it blue, thank you very much.

	Pos nextPos(Pos pos) {
		// Get my track from display
		return cd.nextPos(no, pos);
	}

	boolean atGate(Pos pos) {
		return pos.equals(startpos);
	}

	private boolean atBarrier(Pos pos) {
		// Checks if current car is at the barrier
		return pos.equals(cd.getBarrierPos(no));
	}

	private void initCar() throws InterruptedException {
		//Create instance
		car = cd.newCar(no, col, startpos);
		//Note down the current position
		curpos = startpos;
		//Lock the tile
		lockTile(startpos);
		//Enter the playground
		cd.register(car);
	}

	private void updatePosition() throws InterruptedException {
		//Note the next position
		newpos = nextPos(curpos);
		// Check if we are entering the alley
		if (newpos.equals(alleyEnter)) {
			alley.enter(no);
		}
		//Claim new tile
		lockTile(newpos);
		car.driveTo(newpos);
		//Unlock previous tile
		unlockTile(curpos);
		
		//check if the new position is leaving the alley
		if (newpos.equals(alleyLeave)) {
			alley.leave(no);
		}
		//Update position
		curpos = newpos;

	}

	public void run() {
		try {
			//Initialise car
			initCar();
			while (true) {
				//Check if we are at the gate
				if (atGate(curpos)) {
					mygate.pass();
					car.setSpeed(chooseSpeed());
				}
				//Or if we are at the barrier.
				if (atBarrier(curpos)) {
					bar.sync(no);
				}
				//Then update our position and go again
				updatePosition();
			}

		} catch (Exception e) {
			cd.println("Exception in Car no. " + no + ". Current speed: " + basespeed);
			System.err.println("Exception in Car no. " + no + ":" + e);
			e.printStackTrace();
		}
	}

	private synchronized void lockTile(Pos p) throws InterruptedException {
		//Don't do anything if you are disabled
		if (!disabled) {
			//Else, lock the tile and note down that you did it
			semTiles[p.row][p.col].P();
			this.claimedTiles.add(p);
		}
	}

	private synchronized void unlockTile(Pos p) {
		//Unlock tiles and forget that you did it.
		if(!semTiles[p.row][p.col].toString().equals("0")) {
			System.out.println("I am unlocking something that is already unlocked: "+ semTiles[p.row][p.col].toString());
		}
		semTiles[p.row][p.col].V();
		this.claimedTiles.remove(p);
		
	}
	//Mass removal of claimed tiles, used for removing a car
	public synchronized void unlockAllTiles() {
		for (Pos p : this.claimedTiles) {
			semTiles[p.row][p.col].V();
		}
		this.claimedTiles.removeAll(claimedTiles);
	}

}

public class CarControl implements CarControlI {

	Barrier bar;
	CarDisplayI cd; // Reference to GUI
	Conductor[] conductor; // Car controllers
	Gate[] gate; // Gates
	Semaphore[][] semTiles;
	Alley alley;

	static final int ROWS = 11;
	static final int COLS = 12;

	public CarControl(CarDisplayI cd) {
		this.cd = cd;
		conductor = new Conductor[9];
		gate = new Gate[9];
		semTiles = new Semaphore[ROWS][COLS];
		bar = new Barrier();
		alley = new Alley();

		// Setup tiles
		for (int i = 0; i < ROWS; i++) {
			for (int j = 0; j < COLS; j++) {
				semTiles[i][j] = new Semaphore(1);
			}
		}
		//Define the alley in terms of positions
		Pos tempAlleyEnter;
		Pos tempAlleyLeave;
		for (int no = 0; no < 9; no++) {
			// Setup alley entry and exit points
			if (no < 3) {// Car 1, 2 and 0 (0 is irrelevant)
				tempAlleyEnter = new Pos(ROWS - 3, 0);
				tempAlleyLeave = new Pos(1, 1);
			} else if (no < 5) {// Car 3 and 4
				tempAlleyEnter = new Pos(ROWS - 2, 2);
				tempAlleyLeave = new Pos(1, 1);
			} else { // Car 5-8
				tempAlleyEnter = new Pos(1, 0);
				tempAlleyLeave = new Pos(ROWS - 1, 2);
			}
			//Create a gate
			gate[no] = new Gate();
			//Build constructor
			conductor[no] = new Conductor(no, cd, gate[no], semTiles, alley, bar, tempAlleyEnter, tempAlleyLeave);
			conductor[no].setName("Conductor-" + no);
			//Start the thread
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
		conductor[0].printTrackDebug();
	}

	public void barrierSet(int k) {
		try {
			bar.barrierSet(k);
		} catch (IndexOutOfBoundsException e) {
			// Prints the error to the console
			cd.println(e.toString());
		}
	}
	//Housekeeping function to ensure cars removed do not leave "ghosts" waiting in the entrance
	private void clearAlleyEntrance(Conductor c) {
		if (c.curpos.equals(new Pos(ROWS - 2, 2)) || c.curpos.equals(new Pos(ROWS - 3, 0))) {
			alley.downWaiting = false;
		} else if (c.curpos.equals(new Pos(1, 0))) {
			alley.upWaiting = false;
		}

	}

	public synchronized void removeCar(int no) {
		Conductor cond = conductor[no];
		//If the car isn't already disabled
		if (!cond.disabled) {
			//Disable it
			cond.disabled = true;
			//Remove from simulation
			cd.deregister(cond.car);
			//Unlock all tiles
			cond.unlockAllTiles();
			//Make as disabled in the alley
			alley.disableCar(no);
			//leave the alley
			alley.leave(no);
			//Clear individual wait
			alley.waitForOpposing[no - 1] = false;
			//Clear direction wait
			clearAlleyEntrance(cond);
		} else {
			cd.println("Car already removed");
		}
	}

	public synchronized void restoreCar(int no) {
		//If the car is in fact disabled
		if (conductor[no].disabled) {
			//Enable it in the alley
			alley.enableCar(no);
			//Spawn a new conductor
			conductor[no] = new Conductor(no, cd, gate[no], semTiles, alley, bar, conductor[no].alleyEnter,
					conductor[no].alleyLeave);
			conductor[no].setName("Conductor-" + no);
			conductor[no].start();
		} else {
			cd.println("Car already exist");
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

class Alley {
	int currentCars = 0;
	final int MAX_NO_CARS = 8;
	Boolean curDir = false; // False = up, True = down
	Boolean upWaiting = false; // Flag
	Boolean downWaiting = false;// Flag
	boolean[] carInAlley = new boolean[MAX_NO_CARS];
	boolean[] waitForOpposing = new boolean[MAX_NO_CARS];
	private boolean[] carDisabled = new boolean[MAX_NO_CARS];

	//Initialize disabled array
	public Alley() {
		for (int i = 0; i < MAX_NO_CARS; i++) {
			getCarDisabled()[i] = false;
		}
	}
	//enable/disable switches
	public synchronized void disableCar(int no) {
		getCarDisabled()[no - 1] = true;
	}

	public synchronized void enableCar(int no) {
		getCarDisabled()[no - 1] = false;
	}
	//When a car is waiting, it is noted which direction it is coming from and that direction is now waiting for the oncoming to complete.
	private void setWaitingDirection(int no) {
		//If moving down(5-8)
		if (no > 4) {
			if (!downWaiting) {
				upWaiting = true;
			}
		} else { //Moving up, (1-4)
			if (!upWaiting) {
				downWaiting = true;
			}
		}
	}

	public synchronized void enter(int no) throws InterruptedException {
		//Find the opposite direction
		int invoffSet = no > 4 ? 4 : 0;
		//Predicate describing if a direction is waiting
		boolean opposingIsWaiting = no > 4 ? downWaiting : upWaiting;
		//Predicate describing the absence of oncoming cars
		boolean opposingDisabled = (getCarDisabled()[invoffSet] &&
									getCarDisabled()[invoffSet + 1]&&
									getCarDisabled()[invoffSet + 2] &&
									getCarDisabled()[invoffSet + 3]);
		//Entry guard, Read as:
		//If there are cars in the alley and they are not going in your direction, OR
		//You have been through once and the opposite side is waiting
		//Then you wait.
		while (((currentCars != 0 && no > 4 != curDir) || (waitForOpposing[no - 1] && opposingIsWaiting))) {
			if (!getCarDisabled()[no - 1]) {
			setWaitingDirection(no);
			}
			
			this.wait();
		}
		//If you are still active
		if (!getCarDisabled()[no - 1]) {
			//You can only wait for opposing cars, if there are any.
			if (!opposingDisabled) {
				waitForOpposing[no - 1] = true;
			}
			//Update alley to reflect the car entry.
			carInAlley[no - 1] = true;
			curDir = no > 4;
			currentCars++;
		}

	}

	public synchronized void leave(int no) {
		//Get the cars
		int offSet = no > 4 ? 0 : 4;
		//When the car is in the alley going the right way
		if (no > 4 == curDir && carInAlley[no - 1]) {
			// Notes that the car has left the alley
			carInAlley[no - 1] = false;
			currentCars--;
			if (currentCars <= 0) {
				// In case that this is the last car, release the waiting cars
				currentCars = 0;

				for (int i = offSet; i < offSet + 4; i++) {
					waitForOpposing[i] = false;
				}
				if (no > 4) {
					upWaiting = false;
				} else {
					downWaiting = false;
				}
				this.notifyAll();
			}

		} else {
			//If car is leaving the alley without being in the alley, then the car have been removed remotly
			boolean groupWaiting = no > 4 ? downWaiting : upWaiting;
			//If one in your own group is waiting release the others. This works so that the last car removed will always enable the waiting cars
			if (!groupWaiting) {
				for (int i = offSet; i < offSet + 4; i++) {
					waitForOpposing[i] = false;
				}
				//If noone is waiting, reset the waiting flags
				upWaiting = false;
				downWaiting = false;
				waitForOpposing[no - 1] = false;
				//Wake up the waiting cars
				this.notifyAll();
			}
		}
	}

	public boolean[] getCarDisabled() {
		//Getter
		return carDisabled;
	}

}

class Barrier {

	final int MAX_NO_CARS = 9;

	Boolean flag = false;
	int counter = 0;
	int threshold = MAX_NO_CARS;
	int newThreshold = threshold;
	boolean[] carWaiting = new boolean[MAX_NO_CARS];

	public synchronized void sync(int no) throws InterruptedException {
		if (flag) {
			// Increments the number of cars currently waiting
			carWaiting[no] = true;
			counter++;
			if (counter >= threshold) {
				// If the number of waiting cars exceeds the threshold free the other cars
				freeCars();
			} else {
				// Otherwise wait

				this.wait();

			}

		}
	}

	public synchronized void on() {
		flag = true;
	}

	public synchronized void off() {
		flag = false;
		freeCars();
	}

	// Setting the threshold for the amount of cars that the barrier keeps back
	public synchronized void barrierSet(int k) throws IndexOutOfBoundsException {
		if (k <= MAX_NO_CARS) {
			// Sets the new threshold
			newThreshold = k;
			// Checks if there currently is waiting more than the new threshold (This
			// updates the threshold)
			if (counter >= newThreshold) {
				freeCars();
				// If the new threshold is less than the old, or there are no cars, update the
				// threshold immediately
			} else if (threshold > newThreshold || counter == 0) {
				threshold = newThreshold;
			}
		} else {
			throw new IndexOutOfBoundsException("Threshold greater than Max number of cars");
		}
	}

	private void freeCars() {
		//frees all cars by notification and clearing of carWaiting
		this.notifyAll();
		for (int i = 0; i < MAX_NO_CARS; i++) {
			carWaiting[i] = false;
		}
		counter = 0;
		threshold = newThreshold;
	}

	// Removes the car as waiting ( Used to remotely remove car)
	public synchronized boolean carRemove(int no) {
		if (carWaiting[no]) {
			carWaiting[no] = false;
			counter--;
			return true;
		}
		return false;
	}

}
