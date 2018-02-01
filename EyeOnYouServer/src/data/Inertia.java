package data;

/**
* Inertia object is used to record inertial data in 3-axis in every sample.
*
* @author  WeiChun
*/
public class Inertia {
	
	private double[] acc;
	private double[] gyro;
	
	public Inertia() {
		
	}
	
	public Inertia(double acc_x, double acc_y, double acc_z) {
		this.setAcc(new double[] {acc_x, acc_y, acc_z});
	}
	
	public Inertia(double acc_x, double acc_y, double acc_z, double gyro_x, double gyro_y, double gyro_z) {
		this.setAcc(new double[] {acc_x, acc_y, acc_z});
		this.setGyro(new double[] {gyro_x, gyro_y, gyro_z});
	}

	public double[] getAcc() {
		return acc;
	}

	public void setAcc(double[] acc) {
		this.acc = acc;
	}

	public double[] getGyro() {
		return gyro;
	}

	public void setGyro(double[] gyro) {
		this.gyro = gyro;
	}

}
