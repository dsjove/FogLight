package com.ociweb.iot.maker;

import com.ociweb.gl.api.Behavior;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.TelemetryConfig;
import com.ociweb.gl.impl.ChildClassScanner;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.gl.impl.stage.ReactiveManagerPipeConsumer;
import com.ociweb.iot.hardware.HardwareImpl;
import com.ociweb.iot.hardware.impl.SerialInputSchema;
import com.ociweb.iot.hardware.impl.edison.GroveV3EdisonImpl;
import com.ociweb.iot.hardware.impl.grovepi.*;
import com.ociweb.iot.hardware.impl.test.TestHardware;
import com.ociweb.pronghorn.iot.ReactiveIoTListenerStage;
import com.ociweb.pronghorn.iot.i2c.I2CBacking;
import com.ociweb.pronghorn.iot.schema.GroveRequestSchema;
import com.ociweb.pronghorn.iot.schema.GroveResponseSchema;
import com.ociweb.pronghorn.iot.schema.I2CCommandSchema;
import com.ociweb.pronghorn.iot.schema.I2CResponseSchema;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.ScriptedNonThreadScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class FogRuntime extends MsgRuntime<HardwareImpl, ListenerFilterIoT>  {

	private static boolean isRunning = false;
	public static final int I2C_WRITER      = FogCommandChannel.I2C_WRITER;
	public static final int PIN_WRITER      = FogCommandChannel.PIN_WRITER;
	public static final int SERIAL_WRITER   = FogCommandChannel.SERIAL_WRITER;
	public static final int BT_WRITER       = FogCommandChannel.BT_WRITER;

	private static final Logger logger = LoggerFactory.getLogger(FogRuntime.class);

	private static final int i2cDefaultLength = 300;
	private static final int i2cDefaultMaxPayload = 16;
	
	public static final int i2cResponseDefaultLength = 128;
	public static final int i2cResponseDefaultMaxPayload = 64;
	
	

	private static final byte edI2C = 6;

	static final String PROVIDED_HARDWARE_IMPL_NAME = "com.ociweb.iot.hardware.impl.ProvidedHardwareImpl";

	private boolean disableHardwareDetection;

	public FogRuntime() {
		this(new String[0]);
	}
	public FogRuntime(String name) {
		this(new String[0],name);
	}

	public FogRuntime(String[] args) {
		super(args,null);
        disableHardwareDetection = this.hasArgument("disableHardwareDetection", "--dhd");
	}
	
	public FogRuntime(String[] args, String name) {
		super(args,name);
        disableHardwareDetection = this.hasArgument("disableHardwareDetection", "--dhd");
	}


	public Hardware getHardware(){
		if(this.builder==null){

			if (!disableHardwareDetection) {///////////////
				//setup system for binary binding in case Zulu is found on Arm
				//must populate os.arch as "arm" instead of "aarch32" or "aarch64" in that case, JIFFI is dependent on this value.
				if (System.getProperty("os.arch", "unknown").contains("aarch")) {
					System.setProperty("os.arch", "arm"); //TODO: investigate if this a bug against jiffi or zulu and inform them
				}
	
				long startTime = System.currentTimeMillis();
	
				// Detect provided hardware implementation.
				// TODO: Should this ONLY occur on Android devices?
				try {
					Class.forName("android.app.Activity");
					logger.trace("Detected Android environment. Searching for {}.", PROVIDED_HARDWARE_IMPL_NAME);
	
					try {
						Class<?> clazz = Class.forName(PROVIDED_HARDWARE_IMPL_NAME);
						logger.trace("Detected {}.", PROVIDED_HARDWARE_IMPL_NAME);
						try {
							this.builder = (HardwareImpl) clazz.getConstructor(GraphManager.class).newInstance(gm);
							return this.builder;
						} catch (NoSuchMethodException e) {
							logger.warn(
									"{} does not provide a single argument constructor that accepts a GraphManager. Continuing native hardware detection.", PROVIDED_HARDWARE_IMPL_NAME);
						} catch (Throwable e) {
							logger.warn(
									"Unable to instantiate {}. Continuing native hardware detection.", PROVIDED_HARDWARE_IMPL_NAME, e);
						}
					} catch (ClassNotFoundException e) {
						logger.trace("No {} is present.", PROVIDED_HARDWARE_IMPL_NAME);
					}
				} catch (ClassNotFoundException ignored) { }
	
				long detectDuration = System.currentTimeMillis()-startTime;
				if (detectDuration>500) {
					logger.info("android detect duration {} ms ",detectDuration);
				}
				////////////////////////
				//The best way to detect the pi or edison is to first check for the expected matching i2c implmentation
				///////////////////////
				PiModel pm = null;
				BeagleBoneModel bm = null;
				I2CBacking i2cBacking = null;
	
	
				//			else if((bm = BeagleBoneModel.detect()) != BeagleBoneModel.Unknown) { //NOTE: this requres Super user to run
	//				this.builder = new TestHardware(gm, args);
	//				logger.info("Detected running on " + bm);
	//			}
				
				
				if ((pm = PiModel.detect()) != PiModel.Unknown){ 
					logger.info("Detected running on " + pm);
					this.builder = new GrovePiHardwareImpl(gm, args, pm.i2cBus());
					
				} 
				else if(WindowsDesktopModel.detect() != WindowsDesktopModel.Unknown) {
					this.builder = new TestHardware(gm, args);
					logger.info("Detected running on Windows, test mock hardware will be used");
				}
				else if(LinuxDesktopModel.detect() != LinuxDesktopModel.Unknown) {
					this.builder = new TestHardware(gm, args);
					logger.info("Detected Running on Linux, test mock hardware will be used");
					
				}	
				else if(MacModel.detect() != MacModel.Unknown) {
					this.builder = new TestHardware(gm, args);
					logger.info("Detected running on Mac, test mock hardware will be used");
	
				}
				else if (null != (this.builder = new GroveV3EdisonImpl(gm, args, edI2C)).getI2CBacking() ) {
					logger.info("Detected running on Edison");
					System.out.println("You are running on the Edison hardware.");
				} 
				else {
					this.builder = new TestHardware(gm, args);
					logger.info("Unrecognized hardware, test mock hardware will be used");
				}
			} else 
			
			{
				this.builder = new TestHardware(gm, args);
				logger.info("Hardware detection disabled on the command line, now using mock hardware.");
			}

		}
		return this.builder;
	}


	public FogCommandChannel newCommandChannel() {

		int instance = -1;

		PipeConfigManager pcm = buildPipeManager();

		return this.builder.newCommandChannel(instance, pcm);

	}

	public FogCommandChannel newCommandChannel(int features) {

		int instance = -1;

		PipeConfigManager pcm = buildPipeManager();

		return this.builder.newCommandChannel(features, instance, pcm);

	}

	protected PipeConfigManager buildPipeManager() {
		PipeConfigManager pcm = super.buildPipeManager();
		pcm.addConfig(new PipeConfig<GroveRequestSchema>(GroveRequestSchema.instance, defaultCommandChannelLength));
		pcm.addConfig(new PipeConfig<I2CCommandSchema>(I2CCommandSchema.instance, i2cDefaultLength,i2cDefaultMaxPayload));
		pcm.addConfig(defaultCommandChannelLength,0,TrafficOrderSchema.class );
		return pcm;
	}

	public FogCommandChannel newCommandChannel(int features, int customChannelLength) {

		int instance = -1;

		PipeConfigManager pcm = new PipeConfigManager();
		pcm.addConfig(customChannelLength,0,GroveRequestSchema.class);
		pcm.addConfig(customChannelLength, defaultCommandChannelMaxPayload, I2CCommandSchema.class);
		pcm.addConfig(customChannelLength, defaultCommandChannelMaxPayload, MessagePubSub.class );
		pcm.addConfig(customChannelLength,0,TrafficOrderSchema.class);

		return this.builder.newCommandChannel(features, instance, pcm);

	}

	public ListenerFilterIoT addRotaryListener(RotaryListener listener) {
		return registerListener(listener);
	}

	public ListenerFilterIoT addAnalogListener(AnalogListener listener) {
		return registerListener(listener);
	}

	public ListenerFilterIoT addDigitalListener(DigitalListener listener) {
		return registerListener(listener);
	}

	public ListenerFilterIoT addSerialListener(SerialListener listener) {
		return registerListener(listener);
	}

	public ListenerFilterIoT registerListener(Behavior listener) {
		return registerListenerImpl(listener);
	}

	public ListenerFilterIoT addImageListener(ImageListener listener) {
		switch (builder.getPlatformType()) {
			case GROVE_PI: case TEST:
				return registerListener(listener);
			default:
				throw new UnsupportedOperationException("Image listeners are not supported for [" +
						builder.getPlatformType() +
						"] hardware");
		}
	}

	public ListenerFilterIoT addI2CListener(I2CListener listener) {
		return registerListenerImpl(listener);
	}
	
	////
	

	public ListenerFilterIoT addRotaryListener(String id, RotaryListener listener) {
		return registerListener(id, listener);
	}

	public ListenerFilterIoT addAnalogListener(String id, AnalogListener listener) {
		return registerListener(id, listener);
	}

	public ListenerFilterIoT addDigitalListener(String id, DigitalListener listener) {
		return registerListener(id, listener);
	}

	public ListenerFilterIoT addSerialListener(String id, SerialListener listener) {
		return registerListener(id, listener);
	}

	public ListenerFilterIoT registerListener(String id, Behavior listener) {
		return registerListenerImpl(id, listener);
	}

	public ListenerFilterIoT addImageListener(String id, ImageListener listener) {
		switch (builder.getPlatformType()) {
			case GROVE_PI:
				return registerListener(id, listener);
			default:
				throw new UnsupportedOperationException("Image listeners are not supported for [" +
						builder.getPlatformType() +
						"] hardware");
		}
	}

	public ListenerFilterIoT addI2CListener(String id, I2CListener listener) {
		return registerListenerImpl(id, listener);
	}

	private ListenerFilterIoT registerListenerImpl(Behavior listener) {
		return registerListenerImpl(null, listener);
	}
	
	private ListenerFilterIoT registerListenerImpl(String id, Behavior listener) {

		
		outputPipes = new Pipe<?>[0];
		ChildClassScanner.visitUsedByClass(id, listener, listenerAndNameVisitor, MsgCommandChannel.class);//populates OutputPipes

		/////////
		//pre-count how many pipes will be needed so the array can be built to the right size
		/////////
		int pipesCount = 0;
		if (this.builder.isListeningToI2C(listener) && this.builder.hasI2CInputs()) {
			pipesCount++;
		}
		if (this.builder.isListeningToPins(listener) && this.builder.hasDigitalOrAnalogInputs()) {
			pipesCount++;
		}

		if (this.builder.isListeningToSerial(listener)) {
			pipesCount++;
		}

		if (this.builder.isListeningToCamera(listener)) {
			pipesCount++;
		}
		
		if (this.builder.isListeningToLocationViaCamera(listener)) {
			pipesCount++;
		}
		
		if (this.builder.isListeningToTrainingViaCamera(listener)) {
			pipesCount++;
		}


		pipesCount = addGreenPipesCount(listener, pipesCount);

		Pipe<?>[] inputPipes = new Pipe<?>[pipesCount];


		if (this.builder.isListeningToI2C(listener) && this.builder.hasI2CInputs()) {
            //this is grow2x in case we need to use it for replication.  TODO: we need a way to customize this setting for the response side of a Twig.
			inputPipes[--pipesCount] = new Pipe<I2CResponseSchema>(new PipeConfig<I2CResponseSchema>(I2CResponseSchema.instance,
					FogRuntime.i2cResponseDefaultLength, FogRuntime.i2cResponseDefaultMaxPayload).grow2x());
		}
		if (this.builder.isListeningToPins(listener) && this.builder.hasDigitalOrAnalogInputs()) {
			inputPipes[--pipesCount] = new Pipe<GroveResponseSchema>(new PipeConfig<GroveResponseSchema>(GroveResponseSchema.instance, defaultCommandChannelLength).grow2x());
		}
		if (this.builder.isListeningToSerial(listener) ) {
			inputPipes[--pipesCount] = newSerialInputPipe(new PipeConfig<SerialInputSchema>(SerialInputSchema.instance, defaultCommandChannelLength, defaultCommandChannelMaxPayload).grow2x());
		}
		if (this.builder.isListeningToCamera(listener)) {
			inputPipes[--pipesCount] = ((HardwareImpl) builder).newImageSchemaPipe();
		}
		
		if (this.builder.isListeningToLocationViaCamera(listener)) {
			inputPipes[--pipesCount] = ((HardwareImpl) builder).newLocationSchemaPipe();
		}

		if (this.builder.isListeningToTrainingViaCamera(listener)) {
			inputPipes[--pipesCount] = ((HardwareImpl) builder).newCalibrationSchemaPipe();
		}		

		
		final int httpClientPipeId = netResponsePipeIdx; //must be grabbed before populateGreenPipes
		
		populateGreenPipes(listener, pipesCount, inputPipes);
		
		/////////////////////
		//StartupListener is not driven by any response data and is called when the stage is started up. no pipe needed.
		/////////////////////
		//TimeListener, time rate signals are sent from the stages its self and therefore does not need a pipe to consume.
		/////////////////////
        //this is empty when transducerAutowiring is off
        final ArrayList<ReactiveManagerPipeConsumer> consumers = new ArrayList<ReactiveManagerPipeConsumer>();

        //extract this into common method to be called in GL and FL
		if (transducerAutowiring) {
			inputPipes = autoWireTransducers(id, listener, inputPipes, consumers);
		}
		

		ReactiveIoTListenerStage reactiveListener = builder.createReactiveListener(
				                                    gm, listener,
													inputPipes, 
													outputPipes, 
													consumers,
													parallelInstanceUnderActiveConstruction, id);

		//TODO: this is a new test adding this pipe.
        if (httpClientPipeId != netResponsePipeIdx) {
        	//TODO: We need to add all the Sessions however we do not know this until later.
        	//      
        	
        	reactiveListener.configureHTTPClientResponseSupport(httpClientPipeId);
        }
	
		return reactiveListener;

	}


	private static Pipe<SerialInputSchema> newSerialInputPipe(PipeConfig<SerialInputSchema> config) {
		return new Pipe<SerialInputSchema>(config) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataInputBlobReader<SerialInputSchema> createNewBlobReader() {
				return new SerialReader(this);
			}    		
		};
	}

	@Deprecated
    public static FogRuntime test(FogApp app) {
		FogRuntime runtime = new FogRuntime();
        test(app, runtime);
		return runtime;
    }

	public static boolean testConcurrentUntilShutdownRequested(FogApp app, long timeoutMS) {
		return testConcurrentUntilShutdownRequested(app, new String[0], timeoutMS);
	}

	public static boolean testConcurrentUntilShutdownRequested(FogApp app, String[] args, long timeoutMS) {

		long limit = System.nanoTime() + (timeoutMS*1_000_000L);

		MsgRuntime runtime = run(app, args);

	   	 while (!runtime.isShutdownComplete()) {
	   		if (System.nanoTime() > limit) {
	   				logger.warn("exit due to timeout");
					return false;
	   		}
	   		try {
					Thread.sleep(2);
				} catch (InterruptedException e) {
					return false;
				}
	   	 }
	   	 return true;
	}
	
	public static boolean testUntilShutdownRequested(FogApp app, long timeoutMS) {
		FogRuntime runtime = new FogRuntime(app.getClass().getSimpleName());
		
		ScriptedNonThreadScheduler s = test(app, runtime);
        
        long limit = System.nanoTime() + (timeoutMS*1_000_000L);
        boolean result = true;
        s.startup(true);
    	                
		while (!ScriptedNonThreadScheduler.isShutdownRequested(s)) {

				s.run();
				if (System.nanoTime() > limit) {
					result = false;
					break;
				}
		}		

		s.shutdown();
		return result;
	}

	public static ScriptedNonThreadScheduler test(FogApp app, FogRuntime runtime) {

		//force hardware to TestHardware regardless of where or what platform its run on.
		//this is done because this is the test() method and must behave the same everywhere.
		runtime.builder = new TestHardware(runtime.gm, runtime.args);
		TestHardware hardware = (TestHardware)runtime.getHardware();
		hardware.isInUnitTest = true;

		app.declareConfiguration(runtime.builder);
		GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.builder.getDefaultSleepRateNS());

		runtime.declareBehavior(app);

		runtime.builder.coldSetup(); //TODO: should we add LCD init in the PI hardware code? How do we know when its used?

		runtime.builder.buildStages(runtime);

		runtime.logStageScheduleRates();

		TelemetryConfig telemetryConfig = runtime.builder.getTelemetryConfig();

		if (telemetryConfig != null) {
			runtime.telemetryHost = runtime.gm.enableTelemetry(telemetryConfig.getHost(), telemetryConfig.getPort());

		}
		
		//exportGraphDotFile();

		runtime.setScheduler(new ScriptedNonThreadScheduler(runtime.gm, null, false));
		//= runtime.builder.createScheduler(runtime);
		//for test we do not call startup and wait instead for this to be done by test.

		return (ScriptedNonThreadScheduler)runtime.getScheduler();
	}

	public static FogRuntime run(FogApp app) {
		return run(app,new String[0]);
	}
	public static FogRuntime run(FogApp app, String[] args) throws UnsupportedOperationException {
		if (FogRuntime.isRunning){
			throw new UnsupportedOperationException("An FogApp is already running!");
		}

		long lastTime;
		long nowTime;

		FogRuntime.isRunning = true;
		FogRuntime runtime = new FogRuntime(args);

		lastTime = System.currentTimeMillis();
		Hardware hardware = runtime.getHardware();
		//this default for Fog is slower due to the expected minimum hardware of iot devices
		hardware.setDefaultRate(2_000_000); // 2 ms

		app.declareConfiguration(hardware);
		GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.builder.getDefaultSleepRateNS());
		nowTime = System.currentTimeMillis();
		logger.info("duration {} ms finished declare configuration",nowTime-lastTime);
		lastTime = nowTime;

		runtime.declareBehavior(app);
		nowTime = System.currentTimeMillis();
		logger.info("duration {} ms finished declare behavior", nowTime-lastTime);
		lastTime = nowTime;

		//TODO: at this point realize the stages in declare behavior
		//      all updates are done so create the reactors with the right pipes and names
		//      this change will let us move routes to part of the fluent API plus other benifits..
		//      move all reactor fields into object created early, shell is created here.
		//      register must hold list of all temp objects (linked list to preserve order?)

		System.out.println("To exit app press Ctrl-C");
		runtime.builder.coldSetup(); //TODO: should we add LCD init in the PI hardware code? How do we know when its used?

		runtime.builder.buildStages(runtime);
		runtime.logStageScheduleRates();

		logger.info("{} ms duration {} ms finished building internal graph", nowTime = System.currentTimeMillis(), nowTime-lastTime);
		lastTime = nowTime;

		TelemetryConfig telemetryConfig = runtime.builder.getTelemetryConfig();

		if (telemetryConfig != null) {
			runtime.telemetryHost = runtime.gm.enableTelemetry(telemetryConfig.getHost(), telemetryConfig.getPort());
		}

		//exportGraphDotFile();

		runtime.setScheduler(runtime.builder.createScheduler(runtime));
		runtime.getScheduler().startup();
		logger.info("{} ms duration {} ms finished graph startup", nowTime = System.currentTimeMillis(), nowTime-lastTime);
		lastTime = nowTime;

		return runtime;
	}


}
