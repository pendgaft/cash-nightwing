package topo;

import java.io.*;
import java.util.regex.*;

public class ChinaParser {

	public static void main(String[] args) throws IOException {

		BufferedReader inBuff = new BufferedReader(new FileReader("rawCountryData.xml"));
		BufferedWriter outBuff = new BufferedWriter(new FileWriter("china-as.txt"));
		Pattern asnPattern = Pattern.compile("<asn>(\\d++)</asn>");

		boolean readRegion = false;
		while (inBuff.ready()) {
			String pollString = inBuff.readLine().trim();
			if (readRegion && pollString.contains("<country country_code=")) {
				break;
			} else if (readRegion) {

				Matcher tempMatcher = asnPattern.matcher(pollString);
				if(tempMatcher.find()){
					outBuff.write(tempMatcher.group(1));
					outBuff.newLine();
				}
				
			} else if (pollString
					.contains("<country country_code=\"CN\" country_name=\"China\" country_code_is_region=\"0\">")) {
				readRegion = true;
			}

		}
		inBuff.close();
		outBuff.close();

	}

}
