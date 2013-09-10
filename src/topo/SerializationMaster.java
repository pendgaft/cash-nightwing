package topo;

import java.io.*;
import java.util.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

import decoy.DecoyAS;
import sim.Constants;

public class SerializationMaster {

	private byte[] hashValue;
	private String wardenFile;

	private static final String SERIALFILE_DIRECTORY = "serial/";
	private static final String SERIALFILE_EXT = ".ser";

	public static void main(String args[]) {
		SerializationMaster tester = new SerializationMaster(
				"bowenTest/warden-test.txt");
		System.out.println(tester.convertHashToFileName());
	}

	public SerializationMaster(String wardenFile) {
		this.wardenFile = wardenFile;
		this.buildFileManifest();
	}

	public boolean hasValidSerialFile() {
		String fileName = this.convertHashToFileName();
		File testFileObject = new File(fileName);
		return testFileObject.exists();
	}

	private void buildFileManifest() {
		MessageDigest hasher = null;
		try {
			hasher = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			System.exit(-2);
		}

		this.addToHash(Constants.AS_REL_FILE, hasher);
		this.addToHash(Constants.IP_COUNT_FILE, hasher);
		this.addToHash(Constants.SUPER_AS_FILE, hasher);
		this.addToHash(this.wardenFile, hasher);

		this.hashValue = hasher.digest();
	}

	private void addToHash(String fileName, MessageDigest hashObject) {
		try {
			BufferedReader confFile = new BufferedReader(new FileReader(
					fileName));
			while (confFile.ready()) {
				hashObject.update(confFile.readLine().getBytes());
			}
			confFile.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-2);
		}

	}

	private String convertHashToFileName() {
		Formatter format = new Formatter();
		for (byte b : this.hashValue) {
			format.format("%02x", b);
		}
		String formatResult = format.toString();
		format.close();
		return SerializationMaster.SERIALFILE_DIRECTORY + formatResult
				+ SerializationMaster.SERIALFILE_EXT;
	}

	public void loadSerializationFile(HashMap<Integer, DecoyAS> activeTopo) {
		try {
			ObjectInputStream serialIn = new ObjectInputStream(
					new FileInputStream(this.convertHashToFileName()));
			List<Integer> sortedAS = this.buildSortedASNList(activeTopo
					.keySet());
			for (int tASN : sortedAS) {
				activeTopo.get(tASN).loadASFromSerial(serialIn);
			}
			serialIn.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public void buildSerializationFile(HashMap<Integer, DecoyAS> activeTopo) {
		try {
			ObjectOutputStream serialOut = new ObjectOutputStream(
					new FileOutputStream(this.convertHashToFileName()));
			List<Integer> sortedAS = this.buildSortedASNList(activeTopo
					.keySet());
			for (int tASN : sortedAS) {
				activeTopo.get(tASN).saveASToSerial(serialOut);
			}
			serialOut.close();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}

	private List<Integer> buildSortedASNList(Set<Integer> activeASNs) {
		List<Integer> sortedList = new ArrayList<Integer>();
		sortedList.addAll(activeASNs);
		Collections.sort(sortedList);
		return sortedList;
	}
}
