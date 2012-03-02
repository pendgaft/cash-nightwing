import java.io.File;
import java.io.*;
import java.util.*;

public class runner {
	public static void main(String[] args) {
		File file = new File("asRelations.txt");

		try {
			Scanner sc = new Scanner(file);

			int as1;
			int as2;
			int relation;

			AS[] list = new AS[6];
			for(int a=0; a<list.length; a++) {
				list[a] = new AS(a);
			}
			while(sc.hasNextInt()) {
				as1 = sc.nextInt();
				as2 = sc.nextInt();
				relation = sc.nextInt();
				list[as1].addRelation(list[as2], relation);
			}

			for(AS as : list) {
				as.summary();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

}
