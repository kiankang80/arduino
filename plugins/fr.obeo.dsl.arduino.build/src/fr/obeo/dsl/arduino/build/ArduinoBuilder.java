package fr.obeo.dsl.arduino.build;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

/**
 * Arduino builder is used to cross-compile ino files in order to be able to
 * execute the code on an Arduino board.
 * 
 * @author Melanie Bats<a href="mailto:melanie.bats@obeo.fr">melanie
 *         .bats@obeo.fr</a>
 */
public class ArduinoBuilder {
	/**
	 * Arduino SDK path.
	 */
	private final String arduinoSdk;
	/**
	 * Board tag.
	 */
	private final String boardTag;
	/**
	 * Working directory.
	 */
	private final File directory;

	/**
	 * Arduino builder is used to cross-compile ino files in order to be able to
	 * execute the code on an Arduino board.
	 * 
	 * @param arduinoSdk
	 *            Path to the Arduino SDK
	 * @param boardTag
	 *            Board tag
	 * @param directory
	 *            Working directory
	 */
	public ArduinoBuilder(String arduinoSdk, String boardTag, String directory) {
		this.arduinoSdk = arduinoSdk;
		this.boardTag = boardTag;
		this.directory = new File(directory);
	}

	/**
	 * To test.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		String ARDUINO_SDK = "/home/melanie/Obeo/dev/arduino/arduino-1.0.5/";
		String BOARD_TAG = "uno";
		String FOLDER = "/home/melanie/Obeo/dev/arduino/workspace_sirius/fr.obeo.dsl.arduino.build/test/";

		ArduinoBuilder builder = new ArduinoBuilder(ARDUINO_SDK, BOARD_TAG,
				FOLDER);
		List<String> libraries = new ArrayList<String>();
		libraries.add("Servo");
		builder.compile("Sketch", libraries);
	}

	/**
	 * Compile a sketch and its specific libraries.
	 * 
	 * @param sketchName
	 *            Sketch name
	 * @param libraries
	 *            Specific libraries names.
	 */
	public IStatus compile(String sketchName, List<String> libraries) {
		// Compile sketch
		IStatus sketchStatus = compileSketch(sketchName);
		if(sketchStatus.getSeverity()!= IStatus.OK){
			return sketchStatus;
		}

		// Compile main libraries
		IStatus mainLibrariesStatus = compileMainLibraries();
		if(mainLibrariesStatus.getSeverity()!= IStatus.OK){
			return mainLibrariesStatus;
		}

		// Compile specific libraries
		if (libraries != null) {
			for (String libraryName : libraries) {
				IStatus specificLibraryStatus = compileSpecificLibrary(arduinoSdk + "libraries/" + libraryName
						+ "/" + libraryName);
				if(specificLibraryStatus.getSeverity()!= IStatus.OK){
					return specificLibraryStatus;
				}
			}
		}

		// Link all
		IStatus linkStatus = link(sketchName);
		if(linkStatus.getSeverity()!= IStatus.OK){
			return linkStatus;
		}

		// Generate arduino.hex
		return generateArduinoHex(); 
	}

	public IStatus upload() {
		System.out.println("Upload arduino.hex");
		String command = arduinoSdk + "hardware/tools/avrdude";
		ProcessBuilder builder = new ProcessBuilder(command, "-q", "-V", "-p",
				getMMCU(), "-C", arduinoSdk + "hardware/tools/avrdude.conf",
				"-c", "arduino", "-b", "115200", "-P", "/dev/ttyACM0", "-U",
				"flash:w:arduino.hex:i");
		return executeCommand(directory, builder);
	}

	/**
	 * Generate arduino.hex file.
	 */
	private IStatus generateArduinoHex() {
		System.out.println("Generate arduino.hex");
		String command = arduinoSdk + "hardware/tools/avr/bin/avr-objcopy";
		ProcessBuilder builder = new ProcessBuilder(command, "-j", ".eeprom",
				"--set-section-flags=.eeprom=alloc,load",
				"--change-section-lma", ".eeprom=0", "-O", "ihex",
				"arduino.elf", "arduino.eep");
		executeCommand(directory, builder);
		builder = new ProcessBuilder(command, "-O", "ihex", "-R", ".eeprom",
				"arduino.elf", "arduino.hex");
		return executeCommand(directory, builder);
	}

	/**
	 * Link all.
	 * 
	 * @param sketchName
	 *            Arduino sketch file name
	 */
	private IStatus link(String sketchName) {
		System.out.println("Link all");
		String command = arduinoSdk + "hardware/tools/avr/bin/avr-gcc";
		ProcessBuilder builder = new ProcessBuilder(command, "-mmcu="
				+ getMMCU(), "-Wl,--gc-sections", "-Os", "-o", "arduino.elf",
				sketchName + ".o", "WInterrupts.o", "wiring_analog.o",
				"wiring.o", "wiring_digital.o", "wiring_pulse.o",
				"wiring_shift.o", "CDC.o", "HardwareSerial.o", "HID.o",
				"IPAddress.o", "main.o", "new.o", "Print.o", "Stream.o",
				"Tone.o", "USBCore.o", "WMath.o", "WString.o", "-lc", "-lm");
		return executeCommand(directory, builder);
	}

	/**
	 * Compile specific library.
	 * 
	 * @param libraryName
	 *            Library name
	 */
	private IStatus compileSpecificLibrary(String libraryName) {
		return compileCPPFile(libraryName, libraryName);
	}

	/**
	 * Compile the main Arduino libraries.
	 */
	private IStatus compileMainLibraries() {
		System.out.println("Compile main libraries");
		String arduinoMainLibraryPath = arduinoSdk
				+ "hardware/arduino/cores/arduino/";
		IStatus status = null;
		status = compileCFile(arduinoMainLibraryPath, "WInterrupts");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCFile(arduinoMainLibraryPath, "wiring_analog");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCFile(arduinoMainLibraryPath, "wiring");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCFile(arduinoMainLibraryPath, "wiring_digital");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCFile(arduinoMainLibraryPath, "wiring_pulse");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCFile(arduinoMainLibraryPath, "wiring_shift");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCPPFile(arduinoMainLibraryPath, "CDC");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCPPFile(arduinoMainLibraryPath, "HardwareSerial");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCPPFile(arduinoMainLibraryPath, "HID");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCPPFile(arduinoMainLibraryPath, "IPAddress");
		
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCPPFile(arduinoMainLibraryPath, "main");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCPPFile(arduinoMainLibraryPath, "new");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCPPFile(arduinoMainLibraryPath, "Print");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCPPFile(arduinoMainLibraryPath, "Stream");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCPPFile(arduinoMainLibraryPath, "Tone");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCPPFile(arduinoMainLibraryPath, "USBCore");
		if(status.getSeverity() != IStatus.OK)
			return status;
		status = compileCPPFile(arduinoMainLibraryPath, "WMath");
		if(status.getSeverity() != IStatus.OK)
			return status;
		compileCPPFile(arduinoMainLibraryPath, "WString");
		return status;
	}

	/**
	 * Compile ino file.
	 * 
	 * @param filePath
	 *            Path of the ino file to compile
	 */
	private IStatus compileSketch(String fileName) {
		System.out.println("Compile Sketch");
		String command = arduinoSdk + "hardware/tools/avr/bin/avr-g++";
		ProcessBuilder builder = new ProcessBuilder(command, "-x", "c++",
				"-include", "Arduino.h", "-c", "-mmcu=" + getMMCU(), "-DF_CPU="
						+ getDFCPU(), "-DARDUINO=" + getDArduino(), "-I.", "-I"
						+ arduinoSdk + "hardware/arduino/cores/arduino", "-I"
						+ arduinoSdk + "hardware/arduino/variants/standard",
				"-g", "-Os", "-Wall", "-ffunction-sections", "-fdata-sections",
				"-fno-exceptions", fileName + ".ino", "-o", fileName + ".o");
		return executeCommand(directory, builder);
	}

	/**
	 * Compile C file.
	 * 
	 * @param filePath
	 *            Path of the C file to compile
	 */
	private IStatus compileCFile(String filePath, String fileName) {
		System.out.println("Compile C file : " + filePath + fileName);
		String command = arduinoSdk + "hardware/tools/avr/bin/avr-gcc";
		ProcessBuilder builder = new ProcessBuilder(command, "-c", "-mmcu="
				+ getMMCU(), "-DF_CPU=" + getDFCPU(), "-DARDUINO="
				+ getDArduino(), "-I.", "-I" + arduinoSdk
				+ "hardware/arduino/cores/arduino", "-I" + arduinoSdk
				+ "hardware/arduino/variants/standard", "-g", "-Os", "-Wall",
				"-ffunction-sections", "-fdata-sections", "-std=gnu99",
				filePath + fileName + ".c", "-o", fileName + ".o");
		return executeCommand(directory, builder);
	}

	/**
	 * Compile CPP file.
	 * 
	 * @param filePath
	 *            Path of the CPP file to compile
	 */
	private IStatus compileCPPFile(String filePath, String fileName) {
		System.out.println("Compile CPP file : " + filePath + fileName);
		String command = arduinoSdk + "hardware/tools/avr/bin/avr-g++";
		ProcessBuilder builder = new ProcessBuilder(command, "-c", "-mmcu="
				+ getMMCU(), "-DF_CPU=" + getDFCPU(), "-DARDUINO="
				+ getDArduino(), "-I.", "-I" + arduinoSdk
				+ "hardware/arduino/cores/arduino", "-I" + arduinoSdk
				+ "hardware/arduino/variants/standard", "-g", "-Os", "-Wall",
				"-ffunction-sections", "-fdata-sections", "-fno-exceptions",
				filePath + fileName + ".cpp", "-o", fileName + ".o");
		return executeCommand(directory, builder);
	}

	/**
	 * Execute a command.
	 * 
	 * @param directory
	 *            Current directory
	 * @param builder
	 *            Command to execute
	 */
	private IStatus executeCommand(File directory, ProcessBuilder builder) {
		System.out.println(builder.command());
		builder.directory(directory);
		try {
			Process process = builder.start();
			inheritIO(process.getInputStream(), System.out);
//			inheritIO(process.getErrorStream(), System.err);
			process.waitFor();
			if (process.exitValue() > 0) {
				String error = convertStreamToString(process.getErrorStream());
				return new Status(IStatus.ERROR,
						ArduinoBuilderActivator.PLUGIN_ID, error);
			}
		} catch (IOException e) {
			return new Status(IStatus.ERROR, ArduinoBuilderActivator.PLUGIN_ID,
					e.getMessage(), e);
		} catch (InterruptedException e) {
			return new Status(IStatus.ERROR, ArduinoBuilderActivator.PLUGIN_ID,
					e.getMessage(), e);
		}
		return new Status(IStatus.OK, ArduinoBuilderActivator.PLUGIN_ID, null);
	}

	/**
	 * Get information from {$ARDUINO_SDK}/lib/version.txt in the Arduino SDK.
	 * 
	 * @return
	 */
	private String getDArduino() {
		// TODO Auto-generated method stub
		return "105";
	}

	/**
	 * Get information from {$ARDUINO_SDK}/hardware/arduino/boards.txt file in
	 * the Arduino SDK : {$BOARD_TAG}.build.mcu=xxxx.
	 * 
	 * @return
	 */
	private String getMMCU() {
		// TODO Auto-generated method stub
		return "atmega328p";
	}

	/**
	 * Get information from b{$ARDUINO_SDK}/hardware/arduino/boards.txtt file in
	 * the Arduino SDK : {$BOARD_TAG}.build.f_cpu=xxxx.
	 * 
	 * @return
	 */
	private String getDFCPU() {
		// TODO Auto-generated method stub
		return "16000000L";
	}

	/**
	 * Inherit IO in console.
	 * 
	 * @param src
	 *            Source stream
	 * @param dest
	 *            Destination stream
	 */
	private static void inheritIO(final InputStream src, final PrintStream dest) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				Scanner sc = new Scanner(src);
				while (sc.hasNextLine()) {
					dest.println(sc.nextLine());
				}
				sc.close();
			}
		}).start();
	}

	private static String convertStreamToString(java.io.InputStream is) {
		java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
		return s.hasNext() ? s.next() : "";
	}
}