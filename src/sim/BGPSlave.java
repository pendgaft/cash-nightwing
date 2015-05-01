package sim;

import java.util.Set;
import topo.AS;

public class BGPSlave implements Runnable {

	private BGPMaster workSource;

	public BGPSlave(BGPMaster daBoss) {
		this.workSource = daBoss;
	}

	@Override
	public void run() {
		try {
			while (true) {

				/*
				 * Fetch work from master
				 */
				Set<AS> workSet = this.workSource.getWork();
				BGPMaster.BGPWorkType currentType = this.workSource.getWorkType();

				/*
				 * there is work to do, please do it
				 */
				if (currentType == BGPMaster.BGPWorkType.BGPProcess) {
					for (AS tAS : workSet) {
						tAS.handleAdvertisement();
					}
				} else if (currentType == BGPMaster.BGPWorkType.BGPAdvertise) {
					for (AS tAS : workSet) {
						tAS.mraiExpire();
					}

				}

				this.workSource.reportWorkDone();
			}
		} catch (InterruptedException e) {
			/*
			 * Done w/ work, nothing to report here, just die
			 */
		}

	}

}
