package parsing;

import java.util.HashMap;
import java.io.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import topo.ASTopoParser;

import decoy.DecoyAS;

public class AStoCountry {

	private HashMap<Integer, String> ccMap;

	private static final String UNKNOWN = "unknown";
	private static final String REST = "REST_OF_WORLD";

	public AStoCountry(HashMap<Integer, DecoyAS> topo, String dataFile) throws IOException, SAXException {
		this.ccMap = new HashMap<Integer, String>();

		for (Integer tASN : topo.keySet()) {
			this.ccMap.put(tASN, AStoCountry.UNKNOWN);
		}
		int populated = this.handleXML(dataFile);

		System.out
				.println("Topology is: " + ((double) populated / (double) this.ccMap.size() * 100.0) + "% populated.");
	}
	
	public HashMap<Integer, String> getMap(){
		return this.ccMap;
	}

	private int handleXML(String dataFile) throws IOException, SAXException {
		int populated = 0;
		int duplicate = 0;

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuild = null;
		try {
			docBuild = factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		Document parsedDoc = docBuild.parse(new File(dataFile));
		NodeList root = parsedDoc.getElementsByTagName("asn_results");
		Element rootEle = (Element) root.item(0);
		NodeList countryList = rootEle.getElementsByTagName("country");
		for (int countryCounter = 0; countryCounter < countryList.getLength(); countryCounter++) {
			Element countryEle = (Element) countryList.item(countryCounter);
			String cCode = countryEle.getAttribute("country_code");
			NodeList asList = countryEle.getElementsByTagName("as");
			for (int asCounter = 0; asCounter < asList.getLength(); asCounter++) {
				Element asEle = (Element) asList.item(asCounter);
				String asnStr = ((Element) asEle.getElementsByTagName("asn").item(0)).getTextContent();
				if(asnStr.equals(AStoCountry.REST)){
					continue;
				}
				int asn = Integer.parseInt(asnStr);

				if (this.ccMap.containsKey(asn)) {
					if (this.ccMap.get(asn).equals(AStoCountry.UNKNOWN)) {
						this.ccMap.put(asn, cCode);
						populated++;
					} else {
						duplicate++;
					}
				}
			}
		}

		System.out.println("Duplicates: " + duplicate);
		return populated;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		HashMap<Integer, DecoyAS> usefulASMap = ASTopoParser
				.doNetworkBuild("/scratch/waterhouse/schuch/workspace/cash-nightwing/china-as.txt");
		HashMap<Integer, DecoyAS> prunedASMap = ASTopoParser.doNetworkPrune(usefulASMap);

		AStoCountry self = new AStoCountry(prunedASMap,
				"/scratch/waterhouse/schuch/workspace/cash-nightwing/rawCountryData.xml");
	}

}
