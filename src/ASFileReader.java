import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;


public class ASFileReader {
	public static HashMap<Integer, AS> makeHashMap(String filename){
		File file = new File(filename);

		try {
			Scanner sc = new Scanner(file);
			sc.useDelimiter("\\x7C|\\p{javaWhitespace}+"); // checks for | or whitespace
			
			
			int asn1;
			int asn2;
			AS as1;
			AS as2;
			int relation;

			HashMap<Integer, AS> list = new HashMap<Integer, AS>();

			while(sc.hasNextInt()) {
				asn1 = sc.nextInt();
				asn2 = sc.nextInt();
				relation = sc.nextInt();
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
			
			return list;
		} catch (FileNotFoundException e) {	
			e.printStackTrace();
		}
		return null;
	}
}
