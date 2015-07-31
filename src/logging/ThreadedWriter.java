package logging;

import java.io.*;
import java.util.concurrent.LinkedBlockingQueue;

public class ThreadedWriter extends Writer implements Runnable {

	private BufferedWriter actualOutput;
	private LinkedBlockingQueue<String> internalQueue;
	private boolean open;

	public ThreadedWriter(String fileName) throws IOException {
		super();
		this.actualOutput = new BufferedWriter(new FileWriter(fileName));
		this.internalQueue = new LinkedBlockingQueue<String>();
		this.open = true;
	}

	@Override
	public void run() {
		String currentStr = null;

		try {
			while (this.open) {
				currentStr = this.internalQueue.take();
				this.actualOutput.write(currentStr);
			}
		} catch (InterruptedException e1) {

		} catch (IOException e2) {
			System.err.println("HOLY FUCK IO EXCEPTION");
			e2.printStackTrace();
			System.exit(-2);
		}

		try {
			while ((currentStr = this.internalQueue.poll()) != null) {
				this.actualOutput.write(currentStr);
			}
			this.actualOutput.close();
		} catch (IOException e2) {
			System.err.println("HOLY FUCK IO EXCEPTION");
			e2.printStackTrace();
			System.exit(-2);
		}
	}

	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub
		this.open = false;
	}

	@Override
	public void flush() throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		throw new RuntimeException("Incorrect method to write with.");
	}

	public void write(String outStr) throws IOException {
		if (!this.open) {
			throw new IOException("Tried to write to a closed queue.");
		}

		try {
			this.internalQueue.put(outStr);
		} catch (InterruptedException e) {
			throw new IOException("error inserting into internal queue");
		}
	}

}
