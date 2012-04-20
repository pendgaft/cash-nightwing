import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;


public class ASFileReader {
	public static HashMap<Integer, AS> makeHashMap(String filename){
		File file = new File(filename);

		try {
			Scanner sc = new Scanner(file);
			//sc.useDelimiter("\\x7C|\\p{javaWhitespace}+"); // checks for | or whitespace
			
			
			int asn1;
			int asn2;
			AS as1;
			AS as2;
			int relation;
			String line; // line read in by scanner
			String delimiter = "\\x7C"; // code for | character
			String[] splitList; // list created by split()

			HashMap<Integer, AS> list = new HashMap<Integer, AS>();

			while(sc.hasNextLine()) {
				line = sc.nextLine();
				if(line.charAt(0) == '#') { // check if the line is a comment
					continue;
				} else {
					splitList = line.split(delimiter);
					asn1 = Integer.parseInt(splitList[0]);
					asn2 = Integer.parseInt(splitList[1]);
					relation = Integer.parseInt(splitList[2]);
					as1 = list.get(new Integer(asn1));
					as2 = list.get(new Integer(asn2));

					if(as1 == null) {
						as1 = new AS(asn1);
						list.put(new Integer(asn1), as1);
					}

					if(as2 == null) {
						as2 = new AS(asn2);
						list.put(new Integer(asn2), as2);
					}

					as1.addRelation(as2, relation);
				}
			}
			return list;
		} catch (FileNotFoundException e) {	
			e.printStackTrace();
		}
		return null;
	}
}
