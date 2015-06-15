package sim;

import java.io.*;

public class PerformanceLogger {

	private BufferedWriter outFP;
	private long time;

	public PerformanceLogger(String dir) {
		try {
			this.outFP = new BufferedWriter(new FileWriter(dir + "perf.log"));
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-2);
		}
		this.time = System.currentTimeMillis();
	}

	public void resetTimer() {
		this.time = System.currentTimeMillis();
	}

	public void logTime(String activity) {
		long endTime = System.currentTimeMillis();
		try {
			this.outFP.write(activity + " took " + (endTime - this.time) + "\n");
			this.outFP.flush();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-2);
		}

		this.resetTimer();
	}

	public void done() {
		try {
			this.outFP.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-2);
			;
		}
	}

}
