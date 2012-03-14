import java.util.*;

public class Runner {
	public static void main(String[] args) {
		HashMap<Integer, AS> list = ASFileReader.makeHashMap("asRelations2.txt");
		list.get(new Integer(174)).summary();
		list.get(new Integer(41936)).summary();
		list.get(new Integer(3356)).summary();
	}

}
