package parsing;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import topo.ASRanker;

public class EconExample {

	private static final String FILE_BASE = "/scratch/minerva2/public/nightwingData/";
	private static final String OUTPUT_SUFFIX = "parsingResults_Run";
	private static final String INPUT_SUFFIX = "rawData/";

	public static final Pattern TRANSIT_PATTERN = Pattern.compile("(\\d+),([^,]+),([^,]+),(.+)");
	
	private String fileStub;
	private String transitFile;
	
	public EconExample(String fileStub) {
		this.fileStub = fileStub;
		this.transitFile = FILE_BASE + INPUT_SUFFIX + "transit-" + this.fileStub.toLowerCase() + ".log";
	}

	public void topFlippers(int size) throws IOException{
		HashMap<Integer, Integer> seenMap = new HashMap<Integer, Integer>();
		BufferedReader inBuff = new BufferedReader(new FileReader(this.transitFile));
		int sampleSize = 0;

		while (inBuff.ready()) {
			String pollStr = inBuff.readLine().trim();

			Matcher controlMatcher = MaxParser.ROUND_PATTERN.matcher(pollStr);
			boolean controlFlag = false;
			if (controlMatcher.find()) {
				controlFlag = true;
			} else {
				controlMatcher = MaxParser.SAMPLE_PATTERN.matcher(pollStr);
				if (controlMatcher.find()) {
					controlFlag = true;
				}
			}

			if (controlFlag) {
				sampleSize = Integer.parseInt(controlMatcher.group(1));

				/*
				 * We're ready to actually extract deltas
				 */
				continue;
			}

			if (sampleSize == size) {
				Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					if (Boolean.parseBoolean(dataMatch.group(4)) == true) {
						Integer asn = Integer.parseInt(dataMatch.group(1));
						if(!seenMap.containsKey(asn)){
							seenMap.put(asn, 0);
						}
						seenMap.put(asn, seenMap.get(asn) + 1);
					}
				}
			}
		}
		inBuff.close();
		System.out.println("" + seenMap.size() + " unique ASes seen");

		List<ASRanker> orderedList = new ArrayList<ASRanker>(seenMap.size());
		for(int tASN: seenMap.keySet()){
			orderedList.add(new ASRanker(tASN, seenMap.get(tASN)));
		}
		Collections.sort(orderedList);
		
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(FILE_BASE + OUTPUT_SUFFIX + this.fileStub + "/top-" + size + ".csv"));
		for(int counter = orderedList.size() - 1; counter >= 0; counter--){
			outBuff.write(orderedList.get(counter) + "\n");
		}
		outBuff.close();
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException{
		EconExample self = new EconExample(args[0]);
		self.topFlippers(500);
	}

}
