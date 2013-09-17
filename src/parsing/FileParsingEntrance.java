package parsing;

import java.io.IOException;

public class FileParsingEntrance {

	/**
	 * @param args
	 */
	//private static final String SRCPATH = "/scratch/minerva2/public/nightwingData/";
	private static final String SRCPATH = "nightwingLogs/";
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		FileParsingEngine engine = new FileParsingEngine(SRCPATH + args[0], SRCPATH + args[1]);
		engine.parseFile();
	}

}
