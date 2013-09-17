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
	private static double[] percentile= {0.10, 0.25, 0.50, 0.75, 0.90};
	//private static String OUTPUTPATH = "ParsingData/parsingResults/";
	private static String OUTPUTPATH = "parsingResults/";
	
	public static void main(String args[]) throws IOException{
		FileParsingEngine self = new FileParsingEngine(args[0], args[1]);
		self.parseFile();
	}
	
	public FileParsingEngine(String wardenlog, String transitlog) {
		this.wardenlog = wardenlog;
		this.transitlog = transitlog;
		this.asNum = 0;
		this.wardenNum = 0;
	}
	
	public void parseFile() throws IOException {
		parseWardenLog(this.wardenlog);
		parseTransitLog(this.transitlog);
	}
	
	private void parseWardenLog(String wardenlog) throws IOException {
		boolean firstSampleSize = true;
		int drCount=-1;
		
		/* lists for each sample */
		List<Double> roundOne = new ArrayList<Double>();
		List<Double> roundTwo = new ArrayList<Double>();
		List<Double> delta = new ArrayList<Double>();
		/* lists for each size */
		List<Double> roundOneList = new ArrayList<Double>();
		List<Double> roundTwoList = new ArrayList<Double>();
		List<Double> deltaList = new ArrayList<Double>();
		
		BufferedReader fBuff = new BufferedReader(new FileReader(wardenlog));
		BufferedWriter wardenRoundOneWriter = new BufferedWriter(new FileWriter(FileParsingEngine.OUTPUTPATH + "wardenLog_round1.txt"));
		BufferedWriter wardenRoundTwoWriter = new BufferedWriter(new FileWriter(FileParsingEngine.OUTPUTPATH + "wardenLog_round2.txt"));
		BufferedWriter wardenDeltaWriter = new BufferedWriter(new FileWriter(FileParsingEngine.OUTPUTPATH + "wardenLog_delta.txt"));
		
		/* each loop is for one sample including three rounds*/
		while (fBuff.ready()) {
			
			roundOne.clear();
			roundTwo.clear();
			delta.clear();

			String pollString = fBuff.readLine().trim();
			
			/* for &&&, which is terminator for size */
			if (pollString.equals("&&&")) {
				if (!firstSampleSize) {
					/* sort and write out data */
					writeResultOfOneSampleSize(drCount, roundOneList, wardenRoundOneWriter);
					writeResultOfOneSampleSize(drCount, roundTwoList, wardenRoundTwoWriter);
					writeResultOfOneSampleSize(drCount, deltaList, wardenDeltaWriter);
					
					/* reset the data */
					roundOneList.clear();
					roundTwoList.clear();
					deltaList.clear();
				}
				/* need to read one more line marking round one, line of ### */
				pollString = fBuff.readLine().trim();
			}
			
			/* for the data of the first round,
			 * get the number of wardens if it is the first sample size, after that,
			 * just ignore all since the number is all the same for one network
			 */
			drCount = this.getDrCount(pollString.substring(FileParsingEngine.DRCOUNTPOSITION, pollString.length()));
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
				roundOne.add(Double.parseDouble(pollToks.nextToken()));
			}
			
			/* separation between rounds, i.e. line *** */
			pollString = fBuff.readLine().trim();
			
			/* parse the third round */
			for (int i = 0; i < this.wardenNum; ++i) {
				pollString = fBuff.readLine().trim();
				StringTokenizer pollToks = new StringTokenizer(pollString, ",");
				int asn = Integer.parseInt(pollToks.nextToken());
				roundTwo.add(Double.parseDouble(pollToks.nextToken()));
				delta.add(roundTwo.get(i) - roundOne.get(i));
			}
			
			/* finish one sample and store the median of each sample into the list which is for one size */
			roundOneList.add(this.getGivenPercentile(roundOne, 0.5));
			roundTwoList.add(this.getGivenPercentile(roundTwo, 0.5));
			deltaList.add(this.getGivenPercentile(delta, 0.5));
		}
		
		/* write the last set results of the sample size */
		writeResultOfOneSampleSize(drCount, roundOneList, wardenRoundOneWriter);
		writeResultOfOneSampleSize(drCount, roundTwoList, wardenRoundTwoWriter);
		writeResultOfOneSampleSize(drCount, deltaList, wardenDeltaWriter);
		
		fBuff.close();
		wardenRoundOneWriter.close();
		wardenRoundTwoWriter.close();
		wardenDeltaWriter.close();
	}

	private void parseTransitLog(String transitlog) throws IOException {
		boolean firstSampleSize = true;
		int drCount = -1;
		double sumOfDeltaDecoyingDR, sumOfNotDeltaDecoyingDR;
		/* lists for each size */
		List<Double> aveDeltaDecoyingDRList = new ArrayList<Double>();
		List<Double> aveDeltaNotDecoyingDRList = new ArrayList<Double>();
		
		BufferedReader fBuff = new BufferedReader(new FileReader(transitlog));
		BufferedWriter transitDecoyingDRWriter = new BufferedWriter(new FileWriter(FileParsingEngine.OUTPUTPATH + "transitLog_decoyDR.txt"));
		BufferedWriter transitNotDecoyingDRWriter = new BufferedWriter(new FileWriter(FileParsingEngine.OUTPUTPATH + "transitLog_NotDecoyDR.txt"));
		
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
			drCount = this.getDrCount(pollString.substring(3, pollString.length()));
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
		StringTokenizer pollToks = new StringTokenizer(pollString, ",");
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
		for (int i = 0; i < FileParsingEngine.percentile.length; ++i) {
			out.write(drCount + ", " + this.getGivenPercentile(list, FileParsingEngine.percentile[i]) + ", ");
		}
		out.write("\n");
	}
}

