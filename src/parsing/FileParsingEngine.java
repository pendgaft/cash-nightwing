import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

public class FileParsingEngine {
	private String wardenlog;
	private String transitlog;
	private int asNum;
	private int wardenNum;
	
	private static int DRCOUNTPOSITION = 3;
	/** the percentile to calculate */
	private static double[] percentiles= {0.10, 0.25, 0.50, 0.75, 0.90};
	//private String outputFile = "parsingResults/";
	private String outputFile = "../../../../../../scratch/minerva2/public/nightwingData/";

	
	public static void main(String args[]) throws IOException{
		if (args.length != 3) {
			System.out.println("Usage: ./FileParsingEngine <warden file> <transit file> <output file>");
			return;
		}
		//this.outputFile = this.outputFile + args[2];
		//System.out.println(this.outputFile);
		FileParsingEngine self = new FileParsingEngine(args[0], args[1], args[2]);
		self.parseFile();
	}
	
	public FileParsingEngine(String wardenlog, String transitlog, String outputFile) {
		this.wardenlog = wardenlog;
		this.transitlog = transitlog;
		this.asNum = 0;
		this.wardenNum = 0;
		this.outputFile += outputFile + "/";
		System.out.println(this.outputFile);
	}
	
	public void parseFile() throws IOException {
		parseWardenLog(this.wardenlog);
		parseTransitLog(this.transitlog);
	}
	
	private void parseWardenLog(String wardenlog) throws IOException {
		boolean firstSampleSize = true;
		int drCount=-1;
		
		/* lists for each sample */
		List<Double> roundOneCleanPath = new ArrayList<Double>();
		List<Double> roundTwoCleanPath = new ArrayList<Double>();
		List<Double> deltaCleanPath = new ArrayList<Double>();
		List<Double> incrementCleanPath = new ArrayList<Double>();
		
		List<Double> roundOneCleanAS = new ArrayList<Double>();
		List<Double> roundTwoCleanAS = new ArrayList<Double>();
		List<Double> deltaCleanAS = new ArrayList<Double>();
		List<Double> incrementCleanAS = new ArrayList<Double>();
		
		/* lists for each size */
		List<Double> roundOneCleanPathList = new ArrayList<Double>();
		List<Double> roundTwoCleanPathList = new ArrayList<Double>();
		List<Double> deltaCleanPathList = new ArrayList<Double>();
		List<Double> incrementCleanPathList = new ArrayList<Double>();
		
		List<Double> roundOneCleanASList = new ArrayList<Double>();
		List<Double> roundTwoCleanASList = new ArrayList<Double>();
		List<Double> deltaCleanASList = new ArrayList<Double>();
		List<Double> incrementCleanASList = new ArrayList<Double>();
		
		BufferedReader fBuff = new BufferedReader(new FileReader(wardenlog));
		BufferedWriter wardenRoundOneCleanPathWriter = new BufferedWriter(new FileWriter(this.outputFile + "wardenLog_cleanPath_round1.txt"));
		BufferedWriter wardenRoundTwoCleanPathWriter = new BufferedWriter(new FileWriter(this.outputFile + "wardenLog_cleanPath_round2.txt"));
		BufferedWriter wardenDeltaCleanPathWriter = new BufferedWriter(new FileWriter(this.outputFile + "wardenLog_cleanPath_delta.txt"));
		BufferedWriter wardenIncrementCleanPathWriter = new BufferedWriter(new FileWriter(this.outputFile + "wardenLog_cleanPath_increment.txt"));
		
		BufferedWriter wardenRoundOneCleanASWriter = new BufferedWriter(new FileWriter(this.outputFile + "wardenLog_cleanAS_round1.txt"));
		BufferedWriter wardenRoundTwoCleanASWriter = new BufferedWriter(new FileWriter(this.outputFile + "wardenLog_cleanAS_round2.txt"));
		BufferedWriter wardenDeltaCleanASWriter = new BufferedWriter(new FileWriter(this.outputFile + "wardenLog_cleanAS_delta.txt"));
		BufferedWriter wardenIncrementCleanASWriter = new BufferedWriter(new FileWriter(this.outputFile + "wardenLog_cleanAS_increment.txt"));
		
		/* each loop is for one sample including three rounds*/
		while (fBuff.ready()) {
			
			roundOneCleanPath.clear();
			roundTwoCleanPath.clear();
			deltaCleanPath.clear();
			incrementCleanPath.clear();
			
			roundOneCleanAS.clear();
			roundTwoCleanAS.clear();
			deltaCleanAS.clear();
			incrementCleanAS.clear();

			String pollString = fBuff.readLine().trim();
			
			/* for &&&, which is terminator for size */
			if (pollString.equals("&&&")) {
				if (!firstSampleSize) {
					/* sort and write out data */
					writeResultOfOneSampleSize(drCount, roundOneCleanPathList, wardenRoundOneCleanPathWriter);
					writeResultOfOneSampleSize(drCount, roundTwoCleanPathList, wardenRoundTwoCleanPathWriter);
					writeResultOfOneSampleSize(drCount, deltaCleanPathList, wardenDeltaCleanPathWriter);
					writeResultOfOneSampleSize(drCount, incrementCleanPathList, wardenIncrementCleanPathWriter);
					
					writeResultOfOneSampleSize(drCount, roundOneCleanASList, wardenRoundOneCleanASWriter);
					writeResultOfOneSampleSize(drCount, roundTwoCleanASList, wardenRoundTwoCleanASWriter);
					writeResultOfOneSampleSize(drCount, deltaCleanASList, wardenDeltaCleanASWriter);
					writeResultOfOneSampleSize(drCount, incrementCleanASList, wardenIncrementCleanASWriter);
					
					/* reset the data */
					roundOneCleanPathList.clear();
					roundTwoCleanPathList.clear();
					deltaCleanPathList.clear();
					incrementCleanPathList.clear();
					
					roundOneCleanASList.clear();
					roundTwoCleanASList.clear();
					deltaCleanASList.clear();
					incrementCleanASList.clear();
				}
				/* need to read one more line marking round one, line of ### */
				pollString = fBuff.readLine().trim();
			}
			
			/* for the data of the first round,
			 * get the number of wardens if it is the first sample size, after that,
			 * just ignore all since the number is all the same for one network
			 */
			drCount = this.getDrCount(pollString);
			if (firstSampleSize) {
				/* count the number of wardens */
				while (true) {
					pollString = fBuff.readLine().trim();
					if (pollString.charAt(0) == '*') {
						break;
					}
					++this.wardenNum;
				}
				firstSampleSize = false;
			} else {
				for (int i = 0; i < this.wardenNum+1; ++i) {
					pollString = fBuff.readLine().trim();
				}
			}
			
			/* for the data of the second round */
			for (int i = 0; i < this.wardenNum; ++i) {
				pollString = fBuff.readLine().trim();
				StringTokenizer pollToks = new StringTokenizer(pollString, ",");
				int asn = Integer.parseInt(pollToks.nextToken());
				roundOneCleanPath.add(Double.parseDouble(pollToks.nextToken()));
				roundOneCleanAS.add(Double.parseDouble(pollToks.nextToken()));
			}
			
			/* separation between rounds, i.e. line *** */
			pollString = fBuff.readLine().trim();
			
			/* parse the third round */
			for (int i = 0; i < this.wardenNum; ++i) {
				pollString = fBuff.readLine().trim();
				StringTokenizer pollToks = new StringTokenizer(pollString, ",");
				int asn = Integer.parseInt(pollToks.nextToken());
				
				roundTwoCleanPath.add(Double.parseDouble(pollToks.nextToken()));
				deltaCleanPath.add(roundTwoCleanPath.get(i) - roundOneCleanPath.get(i));
				incrementCleanPath.add(deltaCleanPath.get(i) / roundOneCleanPath.get(i));
				
				roundTwoCleanAS.add(Double.parseDouble(pollToks.nextToken()));
				deltaCleanAS.add(roundTwoCleanAS.get(i) - roundOneCleanAS.get(i));
				incrementCleanAS.add(deltaCleanAS.get(i) / roundOneCleanAS.get(i));
			}
			
			/* finish one sample and store the median of each sample into the list which is for one size */
			roundOneCleanPathList.add(this.getGivenPercentile(roundOneCleanPath, 0.5));
			roundTwoCleanPathList.add(this.getGivenPercentile(roundTwoCleanPath, 0.5));
			deltaCleanPathList.add(this.getGivenPercentile(deltaCleanPath, 0.5));
			incrementCleanPathList.add(this.getGivenPercentile(incrementCleanPath, 0.5));
			
			roundOneCleanASList.add(this.getGivenPercentile(roundOneCleanAS, 0.5));
			roundTwoCleanASList.add(this.getGivenPercentile(roundTwoCleanAS, 0.5));
			deltaCleanASList.add(this.getGivenPercentile(deltaCleanAS, 0.5));
			incrementCleanASList.add(this.getGivenPercentile(incrementCleanAS, 0.5));
		}
		
		/* write the last set results of the sample size */
		writeResultOfOneSampleSize(drCount, roundOneCleanPathList, wardenRoundOneCleanPathWriter);
		writeResultOfOneSampleSize(drCount, roundTwoCleanPathList, wardenRoundTwoCleanPathWriter);
		writeResultOfOneSampleSize(drCount, deltaCleanPathList, wardenDeltaCleanPathWriter);
		writeResultOfOneSampleSize(drCount, incrementCleanPathList, wardenIncrementCleanPathWriter);
		
		writeResultOfOneSampleSize(drCount, roundOneCleanASList, wardenRoundOneCleanASWriter);
		writeResultOfOneSampleSize(drCount, roundTwoCleanASList, wardenRoundTwoCleanASWriter);
		writeResultOfOneSampleSize(drCount, deltaCleanASList, wardenDeltaCleanASWriter);
		writeResultOfOneSampleSize(drCount, incrementCleanASList, wardenIncrementCleanASWriter);
		
		fBuff.close();
		wardenRoundOneCleanPathWriter.close();
		wardenRoundTwoCleanPathWriter.close();
		wardenDeltaCleanPathWriter.close();
		wardenIncrementCleanPathWriter.close();
		
		wardenRoundOneCleanASWriter.close();
		wardenRoundTwoCleanASWriter.close();
		wardenDeltaCleanASWriter.close();
		wardenIncrementCleanASWriter.close();
	}

	private void parseTransitLog(String transitlog) throws IOException {
		boolean firstSampleSize = true;
		int drCount = -1;
		double sumOfDeltaDecoyingDR, sumOfNotDeltaDecoyingDR;
		/* lists for each size */
		List<Double> aveDeltaDecoyingDRList = new ArrayList<Double>();
		List<Double> aveDeltaNotDecoyingDRList = new ArrayList<Double>();
		
		BufferedReader fBuff = new BufferedReader(new FileReader(transitlog));
		BufferedWriter transitDecoyingDRWriter = new BufferedWriter(new FileWriter(this.outputFile + "transitLog_decoyDR.txt"));
		BufferedWriter transitNotDecoyingDRWriter = new BufferedWriter(new FileWriter(this.outputFile + "transitLog_NotDecoyDR.txt"));
		
		/* each loop is for one sample including three rounds */
		while (fBuff.ready()) {

			sumOfDeltaDecoyingDR = 0;
			sumOfNotDeltaDecoyingDR = 0;
			
			String pollString = fBuff.readLine().trim();
			
			/* for &&&, which is terminator for size */
			if (pollString.equals("&&&")) {
				if (!firstSampleSize) {
					/* sort and write out data */
					writeResultOfOneSampleSize(drCount, aveDeltaDecoyingDRList, transitDecoyingDRWriter);
					writeResultOfOneSampleSize(drCount, aveDeltaNotDecoyingDRList, transitNotDecoyingDRWriter);
					
					/* reset the data */
					aveDeltaDecoyingDRList.clear();
					aveDeltaNotDecoyingDRList.clear();
				}
				/* need to read one more line for round one, line of ### */
				pollString = fBuff.readLine().trim();
			}
			
			/* for the data of the first round,
			 * get the number of wardens if it is the first sample size, after that,
			 * just ignore all since the number is all the same for one map
			 */
			drCount = this.getDrCount(pollString);
			if (firstSampleSize) {
				/* count the number of wardens */
				while (true) {
					pollString = fBuff.readLine().trim();
					if (pollString.charAt(0) == '*') {
						break;
					}
					++this.asNum;
				}
				firstSampleSize = false;
			} else {
				for (int i = 0; i < this.asNum+1; ++i) {
					pollString = fBuff.readLine().trim();
				}
			}
			
			/* for the data of the second round */
			for (int i = 0; i < this.asNum; ++i) {
				pollString = fBuff.readLine().trim();
				StringTokenizer pollToks = new StringTokenizer(pollString, ",");
				int asn = Integer.parseInt(pollToks.nextToken());
				double money = Double.parseDouble(pollToks.nextToken());
				boolean decoyingDR = Boolean.parseBoolean(pollToks.nextToken());
				if (decoyingDR) {
					sumOfDeltaDecoyingDR -= money;
				} else {
					sumOfNotDeltaDecoyingDR -= money;
				}
			}
			
			/* separation between rounds, i.e. line *** */
			pollString = fBuff.readLine().trim();
			
			/* parse the third round */
			for (int i = 0; i < this.asNum; ++i) {
				pollString = fBuff.readLine().trim();
				StringTokenizer pollToks = new StringTokenizer(pollString, ",");
				int asn = Integer.parseInt(pollToks.nextToken());
				double money = Double.parseDouble(pollToks.nextToken());
				boolean decoyingDR = Boolean.parseBoolean(pollToks.nextToken());
				if (decoyingDR) {
					sumOfDeltaDecoyingDR += money;
				} else {
					sumOfNotDeltaDecoyingDR += money;
				}
			}
			
			/* finish one sample and store the median of each sample into the list which is for one size */
			//System.out.println("decoying : " + sumOfDeltaDecoyingDR + ", not decoying " + sumOfNotDeltaDecoyingDR);
			aveDeltaDecoyingDRList.add(sumOfDeltaDecoyingDR / drCount);
			aveDeltaNotDecoyingDRList.add(sumOfNotDeltaDecoyingDR / (this.asNum - drCount));
		}
		
		/* write the last set results of the sample size */
		writeResultOfOneSampleSize(drCount, aveDeltaDecoyingDRList, transitDecoyingDRWriter);
		writeResultOfOneSampleSize(drCount, aveDeltaNotDecoyingDRList, transitNotDecoyingDRWriter);
		
		fBuff.close();
		transitDecoyingDRWriter.close();
		transitNotDecoyingDRWriter.close();
		
	}
	
	private int getDrCount(String pollString) {
		StringTokenizer pollToks = new StringTokenizer(pollString.substring(FileParsingEngine.DRCOUNTPOSITION, pollString.length()), ",");
		return Integer.parseInt(pollToks.nextToken());
	}
	
	/**
	 * return a percentile value of an unsorted list
	 * @param list
	 * @param length
	 * @param percentile
	 * @return
	 */
	private double getGivenPercentile(List<Double> list, double percentile) {
		Collections.sort(list);
		int pos = (int)(list.size() * percentile);
		if (pos >= list.size())
			pos = list.size()-1;
		
		return list.get(pos);
	}
	
	/**
	 * write one set  of data, or say 10%, 25%, 50%, 75%, and 90%, into a given file
	 * @param drCount
	 * @param list
	 * @param out
	 * @throws IOException
	 */
	private void writeResultOfOneSampleSize(int drCount, List<Double> list, BufferedWriter out) throws IOException {
		Collections.sort(list);
		for (int i = 0; i < FileParsingEngine.percentiles.length; ++i) {
			out.write(drCount + ", " + this.getGivenPercentile(list, FileParsingEngine.percentiles[i]) + ", ");
		}
		out.write("\n");
	}
}
