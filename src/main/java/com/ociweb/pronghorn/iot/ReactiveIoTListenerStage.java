package com.ociweb.pronghorn.iot;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Behavior;
import com.ociweb.gl.impl.stage.ReactiveListenerStage;
import com.ociweb.gl.impl.stage.ReactiveManagerPipeConsumer;
import com.ociweb.gl.impl.stage.ReactiveOperator;
import com.ociweb.gl.impl.stage.ReactiveOperators;
import com.ociweb.iot.hardware.HardwareConnection;
import com.ociweb.iot.hardware.HardwareImpl;
import com.ociweb.iot.hardware.impl.SerialInputSchema;
import com.ociweb.iot.impl.AnalogListenerBase;
import com.ociweb.iot.impl.CalibrationListenerBase;
import com.ociweb.iot.impl.DigitalListenerBase;
import com.ociweb.iot.impl.I2CListenerBase;
import com.ociweb.iot.impl.ImageListenerBase;
import com.ociweb.iot.impl.LocationListenerBase;
import com.ociweb.iot.impl.RotaryListenerBase;
import com.ociweb.iot.impl.SerialListenerBase;
import com.ociweb.iot.maker.AnalogListener;
import com.ociweb.iot.maker.DigitalListener;
import com.ociweb.iot.maker.I2CListener;
import com.ociweb.iot.maker.ListenerFilterIoT;
import com.ociweb.iot.maker.Port;
import com.ociweb.iot.maker.RotaryListener;
import com.ociweb.iot.maker.SerialListener;
import com.ociweb.pronghorn.image.schema.CalibrationStatusSchema;
import com.ociweb.pronghorn.iot.schema.GroveResponseSchema;
import com.ociweb.pronghorn.iot.schema.I2CResponseSchema;
import com.ociweb.pronghorn.image.schema.ImageSchema;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.stage.math.ProbabilitySchema;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.ma.MAvgRollerLong;

public class ReactiveIoTListenerStage extends ReactiveListenerStage<HardwareImpl> implements ListenerFilterIoT {
  
    private static final Logger logger = LoggerFactory.getLogger(ReactiveIoTListenerStage.class); 
    
    protected MAvgRollerLong[] rollingMovingAveragesAnalog;
    protected MAvgRollerLong[] rollingMovingAveragesDigital;    
    private boolean startupCompleted;
    
    protected int[] oversampledAnalogValues;

    private static final int MAX_PORTS = 10;
    
    //for analog values returns the one with the longest run within the last n samples
    protected static final int OVERSAMPLE = 3; //  (COUNT), SAMPLE1, ... SAMPLEn
    protected static final int OVERSAMPLE_STEP = OVERSAMPLE+1;
    
    protected int[] lastDigitalValues;
    protected long[] lastDigitalTimes;
    
    protected boolean[] sendEveryAnalogValue;
    protected boolean[] sendEveryDigitalValue;
    
    
    protected int[] lastAnalogValues;
    protected long[] lastAnalogTimes;

    /////////////////////
    //Listener Filters
    /////////////////////    
    private Port[] includedPorts;//if null then all values are accepted
    private Port[] excludedPorts;//if null then no values are excluded
    private int[] includedI2Cs;//if null then all values are accepted
    private int[] excludedI2Cs;//if null then no values are excluded
		
    /////////////////////
    private Number stageRate;
    
    private DataInputBlobReader serialStremReader; //must be held as we accumulate serial data.

	// Image data.
	private enum ImageDataState {
		EMPTY,
		FRAME_START_BUFFERED,
		FRAME_ROW_BUFFERED
	}
	private class ImageData {
		ImageDataState state = ImageDataState.EMPTY;
		int width;
		int height;
		long timestamp;
		int frameBytesCount;
		byte[] currentRowBytes;
	}
	private final ImageData workingImageData = new ImageData();
    
    public static void initOperators(ReactiveOperators operators) {
    	
    	//Add more supported operators to the system
    	operators
    	  .addOperator(SerialListenerBase.class, 
        		SerialInputSchema.instance,
       		 new ReactiveOperator() {
			@Override
			public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
				((ReactiveIoTListenerStage)r).consumeSerialMessage((SerialListenerBase)target, input);										
			}        		                	 
        })
		.addOperator(ImageListenerBase.class,
					 ImageSchema.instance,
					 new ReactiveOperator() {
			 @Override
			 public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
				 ((ReactiveIoTListenerStage) r).consumeImageMessage((ImageListenerBase) target, input);
			 }
		 })
		.addOperator(LocationListenerBase.class,
				ProbabilitySchema.instance,
				 new ReactiveOperator() {
			 @Override
			 public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
				 ((ReactiveIoTListenerStage) r).consumeLocationMessage((LocationListenerBase) target, input);
			 }
	     })
		.addOperator(CalibrationListenerBase.class,
				CalibrationStatusSchema.instance,
				 new ReactiveOperator() {
		 @Override
		 public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
			 ((ReactiveIoTListenerStage) r).consumeCalibrationMessage((CalibrationListenerBase) target, input);
		 }
		})
        .addOperator(AnalogListenerBase.class,
        		GroveResponseSchema.instance,
	       		 new ReactiveOperator() {
				@Override
				public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
					((ReactiveIoTListenerStage)r).consumeResponseMessage(target, input);										
				}        		                	 
	        })
        .addOperator(DigitalListenerBase.class, 
        		GroveResponseSchema.instance,
	       		 new ReactiveOperator() {
				@Override
				public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
					((ReactiveIoTListenerStage)r).consumeResponseMessage(target, input);										
				}        		                	 
	        })
        .addOperator(RotaryListenerBase.class, 
        		GroveResponseSchema.instance,
	       		 new ReactiveOperator() {
				@Override
				public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
					((ReactiveIoTListenerStage)r).consumeResponseMessage(target, input);										
				}        		                	 
	        })
        .addOperator(I2CListenerBase.class, 
        		I2CResponseSchema.instance,
	       		 new ReactiveOperator() {
				@Override
				public void apply(int index, Object target, Pipe input, ReactiveListenerStage r) {
					((ReactiveIoTListenerStage)r).consumeI2CMessage(target, input);										
				}        		                	 
	        });    	
    }
    
    protected void consumeCalibrationMessage(CalibrationListenerBase target, Pipe<CalibrationStatusSchema> input) {

		while (PipeReader.peekMsg(input, CalibrationStatusSchema.MSG_CYCLECALIBRATED_1)) {
			
			int start = PipeReader.peekInt(input, CalibrationStatusSchema.MSG_CYCLECALIBRATED_1_FIELD_STARTVALUE_12);
			int units = PipeReader.peekInt(input, CalibrationStatusSchema.MSG_CYCLECALIBRATED_1_FIELD_TOTALUNITS_13);
			
			if (target.finishedCalibration(start, units)) {
				//consumed so we consume
				PipeReader.tryReadFragment(input);
				PipeReader.releaseReadLock(input);
			} else {
				//could not be consumed so return to try again later
				return;				
			}
		}
		if (PipeReader.peekMsg(input, -1)) {
			//if shutdown message we just ignore it
			PipeReader.tryReadFragment(input);
			PipeReader.releaseReadLock(input);
		}
  
		
	}

	protected void consumeLocationMessage(LocationListenerBase target, Pipe<ProbabilitySchema> input) {
			
		while (PipeReader.peekMsg(input, ProbabilitySchema.MSG_SELECTION_1)) {
			
			int buckets = PipeReader.peekInt(input, ProbabilitySchema.MSG_SELECTION_1_FIELD_TOTALBUCKETS_13);
			assert(buckets>=1) : "Why was this called without any data?";
			long totalSum = (long)PipeReader.peekInt(input, ProbabilitySchema.MSG_SELECTION_1_FIELD_TOTALSUM_12);
			ChannelReader orderedStream = PipeReader.peekInputStream(input, ProbabilitySchema.MSG_SELECTION_1_FIELD_ORDEREDSELECTIONS_14);
	
			//we are doing the lazy thing and just picking the first one... we need a smarter approach
			long oddsOfRightLocation = orderedStream.readPackedLong();
			int location = (int) orderedStream.readPackedLong();			
			
			if (target.location(location, oddsOfRightLocation, totalSum)) {
				//was consumed, so actually consume the fragment
				PipeReader.tryReadFragment(input);
				
				///
				boolean debug = true;
				
				if (debug) {
			
					//show all the results..
					System.out.print("Detected Location data ");
					Appendables.appendValue(System.out, "Buckets: ", buckets);
					Appendables.appendValue(System.out, "TotalSum:: ", totalSum);
					System.out.println();
					
					ChannelReader reader = PipeReader.inputStream(input, ProbabilitySchema.MSG_SELECTION_1_FIELD_ORDEREDSELECTIONS_14);
										
					
					int x = buckets;
					while (--x>=0) {

						if(totalSum > 0) {
							long pct = ((100000L * orderedStream.readPackedLong()) / totalSum);
							int loc = (int) orderedStream.readPackedLong();

							Appendables.appendValue(System.out, "location: ", loc);
							System.out.append("pct: ");
							Appendables.appendDecimalValue(System.out, pct, (byte) -3);
							System.out.println();
						}
						
					}
					
				}
				
				
				PipeReader.releaseReadLock(input);
			} else {
				return;//consumer did not take message so we need leave and try again later.				
			}	
		}
		if (PipeReader.peekMsg(input, -1)) {
			//if shutdown message we just ignore it
			PipeReader.tryReadFragment(input);
			PipeReader.releaseReadLock(input);
		}
		
		
	}

	public ReactiveIoTListenerStage(GraphManager graphManager, Behavior listener, 
    		                        Pipe<?>[] inputPipes, 
    		                        Pipe<?>[] outputPipes, 
    		                        ArrayList<ReactiveManagerPipeConsumer> consumers,
    		                        HardwareImpl hardware, int parallelInstance, String nameId) {

        
        super(graphManager, listener, inputPipes, outputPipes, consumers, hardware, parallelInstance, nameId);

        if (listener instanceof DigitalListener) {
        	toStringDetails = toStringDetails + "DigitalListener\n";
        }
        if (listener instanceof AnalogListener) {
        	toStringDetails = toStringDetails + "AnalogListener\n";
        }
        if (listener instanceof I2CListener) {
        	toStringDetails = toStringDetails + "I2CListener\n";
        }
        if (listener instanceof SerialListener) {
        	toStringDetails = toStringDetails + "SerialListener\n";
        }
        if (listener instanceof RotaryListener) {
        	toStringDetails = toStringDetails + "RotaryListener\n";
        }
        
        this.builder = hardware;
                   
        //allow for shutdown upon shutdownRequest we have new content
        producer();
    }


	private void setupMovingAverages(HardwareImpl hardware, MAvgRollerLong[] target, HardwareConnection[] con) {
        int i = con.length;
        while (--i >= 0) {            
              target[hardware.convertToPort(con[i].register)] = new MAvgRollerLong(con[i].movingAverageWindowMS/con[i].responseMS);
        }        
    }

    
    protected int findStableReading(int tempValue, int connector) { 
        return findMedian(updateRunLenghtOfActiveValue(tempValue, connector));
    }

    
    private int findMedian(int offset) {
        assert(3 == OVERSAMPLE);//ONLY WORKS FOR 3
        
        int a = oversampledAnalogValues[offset+1];
        int b = oversampledAnalogValues[offset+2];
        int c = oversampledAnalogValues[offset+3];
        
        //TODO: what if we returned the floor?
        
        if (a>b) {
            if (b>c) {
                return b;
            } else {
                return c;
            }
        } else {
            //b > a
            if (a>c) {
                return a;
            } else {
                return c;
            }
        }
        
    }

    private int updateRunLenghtOfActiveValue(int tempValue, int connector) {

        //store this value with the oversamples
        int offset = connector*OVERSAMPLE_STEP;
        int pos =       oversampledAnalogValues[offset];                           
        
        if (--pos<=0) {
            pos = OVERSAMPLE;
        }
        oversampledAnalogValues[offset]     = pos;
        oversampledAnalogValues[offset+pos] = tempValue;
        return offset;
    }
    
    
    @Override
    public void startup() {
    	
        //Init all the moving averages to the right size
        rollingMovingAveragesAnalog = new MAvgRollerLong[MAX_PORTS];
        rollingMovingAveragesDigital = new MAvgRollerLong[MAX_PORTS];
        
        HardwareConnection[] analogInputs = builder.getAnalogInputs();
        HardwareConnection[] digitalInputs = builder.getDigitalInputs();
        
		setupMovingAverages(builder, rollingMovingAveragesAnalog, analogInputs);
              
        setupMovingAverages(builder, rollingMovingAveragesDigital, builder.getDigitalInputs());
          
        lastDigitalValues = new int[MAX_PORTS];
        lastAnalogValues = new int[MAX_PORTS];
        
        sendEveryAnalogValue = new boolean[MAX_PORTS];
        sendEveryDigitalValue = new boolean[MAX_PORTS];
        
        int a = analogInputs.length;
        while (--a>=0) {        	
        	HardwareConnection con = analogInputs[a];
        	//System.out.println("seems wrong to covert this: "+con.register);
        	sendEveryAnalogValue[builder.convertToPort(con.register)] = con.sendEveryValue;
        }
        
        int d = digitalInputs.length;
        while (--d>=0) {        	
        	HardwareConnection con = digitalInputs[d];
        	//System.out.println("seems wrong to covert this: "+con.register);
        	sendEveryDigitalValue[builder.convertToPort(con.register)] = con.sendEveryValue;        	
        }
        
        
        lastDigitalTimes = new long[MAX_PORTS];
        lastAnalogTimes = new long[MAX_PORTS];
                    
        oversampledAnalogValues = new int[MAX_PORTS*OVERSAMPLE_STEP];
        
        stageRate = (Number)GraphManager.getNota(graphManager, this.realStage.stageId,  GraphManager.SCHEDULE_RATE, null);
        
        timeProcessWindow = (null==stageRate? 0 : (int)(stageRate.longValue()/MS_to_NS));
                        
        //Do last so we complete all the initializations first
        super.startup();
    }
    
    protected void consumeI2CMessage(Object listener, Pipe<I2CResponseSchema> p) {

        while (PipeReader.tryReadFragment(p)) {                
                    
                    int msgIdx = PipeReader.getMsgIdx(p);
                    switch (msgIdx) {   
                        case I2CResponseSchema.MSG_RESPONSE_10:
                        	processI2CMessage(listener, p);
                            break;
                        case -1:
                            
                        	this.realStage.requestShutdown();
                            PipeReader.releaseReadLock(p);
                            return;
                           
                        default:
                            throw new UnsupportedOperationException("Unknown id: "+msgIdx);
                        
                    }
                    //done reading message off pipe
                    PipeReader.releaseReadLock(p);
        }
    }

	protected void processI2CMessage(Object listener, Pipe<I2CResponseSchema> p) {

			int addr = PipeReader.readInt(p, I2CResponseSchema.MSG_RESPONSE_10_FIELD_ADDRESS_11);
			long time = PipeReader.readLong(p, I2CResponseSchema.MSG_RESPONSE_10_FIELD_TIME_13);
			int register = PipeReader.readInt(p, I2CResponseSchema.MSG_RESPONSE_10_FIELD_REGISTER_14);

			byte[] backing = PipeReader.readBytesBackingArray(p, I2CResponseSchema.MSG_RESPONSE_10_FIELD_BYTEARRAY_12);
			int position = PipeReader.readBytesPosition(p, I2CResponseSchema.MSG_RESPONSE_10_FIELD_BYTEARRAY_12);
			int length = PipeReader.readBytesLength(p, I2CResponseSchema.MSG_RESPONSE_10_FIELD_BYTEARRAY_12);
			int mask = PipeReader.readBytesMask(p, I2CResponseSchema.MSG_RESPONSE_10_FIELD_BYTEARRAY_12);
		    
		    commonI2CEventProcessing((I2CListenerBase) listener, addr, register, time, backing, position, length, mask);

	}


	
	private void consumeSerialMessage(SerialListenerBase serial, Pipe<SerialInputSchema> p) {
		
		while (PipeReader.tryReadFragment(p)) {
		    int msgIdx = PipeReader.getMsgIdx(p);
		    int consumed = 0;
		    switch(msgIdx) {
		        case SerialInputSchema.MSG_CHUNKEDSTREAM_1:
		        	if (null==serialStremReader) {
		        		serialStremReader = PipeReader.inputStream(p, SerialInputSchema.MSG_CHUNKEDSTREAM_1_FIELD_BYTEARRAY_2);
		        	} else {
		        		serialStremReader.accumHighLevelAPIField(SerialInputSchema.MSG_CHUNKEDSTREAM_1_FIELD_BYTEARRAY_2);
		        	}		 
		        	//let the behavior consume some or all of the serial data
		        	int originalPosition = serialStremReader.absolutePosition();
		        	
		        	//System.err.println("reading from position "+originalPosition);
		        	consumed = serial.message(serialStremReader);
		        	//System.err.println("reading total size "+consumed+" now pos "+serialStremReader.absolutePosition());
		        	
		        	//set position for the next call to ensure we are aligned after the consumed bytes
		        	//the behavior could read past consumed and choose not to consume it yet.
		        	serialStremReader.absolutePosition(originalPosition+consumed);
		        	assert(consumed>=0) : "can not consume negative value";
		        	
		        break;
		        case -1:
		        	this.realStage.requestShutdown();
		        break;
		    }
		    PipeReader.readNextWithoutReleasingReadLock(p);
		    PipeReader.releaseAllPendingReadLock(p, consumed);
		}
		
	}

	protected void consumeImageMessage(ImageListenerBase listener, Pipe<ImageSchema> inputPipe) {

    	// TODO: There may be some code duplication here that could be simplified after testing.

    	// If the last invocation of this method exited due to backpressure
		// (state is not empty), try to relieve the backpressure before continuing.
		if (workingImageData.state != ImageDataState.EMPTY) {
			switch (workingImageData.state) {
				case FRAME_START_BUFFERED:
					if (listener.onFrameStart(workingImageData.width, workingImageData.height, workingImageData.timestamp, workingImageData.frameBytesCount)) {
						workingImageData.state = ImageDataState.EMPTY;
					}

					break;

				case FRAME_ROW_BUFFERED:
					if (listener.onFrameRow(workingImageData.currentRowBytes)) {
						workingImageData.state = ImageDataState.EMPTY;
					}

					break;

				// If the frame is somehow empty, exit.
				case EMPTY: default: break;
			}
		}

    	// Perform normal pipe reads while there is no backpressure.
		while (workingImageData.state == ImageDataState.EMPTY && PipeReader.tryReadFragment(inputPipe)) {
			int msgIdx = PipeReader.getMsgIdx(inputPipe);
			switch(msgIdx) {
				case ImageSchema.MSG_FRAMESTART_1:

					assert workingImageData.state == ImageDataState.EMPTY;

					// Extract message start data.
					workingImageData.width = PipeReader.readInt(inputPipe, ImageSchema.MSG_FRAMESTART_1_FIELD_WIDTH_101);
					workingImageData.height = PipeReader.readInt(inputPipe, ImageSchema.MSG_FRAMESTART_1_FIELD_HEIGHT_201);
					workingImageData.timestamp = PipeReader.readLong(inputPipe, ImageSchema.MSG_FRAMESTART_1_FIELD_TIMESTAMP_301);
					workingImageData.frameBytesCount = PipeReader.readInt(inputPipe, ImageSchema.MSG_FRAMESTART_1_FIELD_FRAMEBYTES_401);
					workingImageData.state = ImageDataState.FRAME_START_BUFFERED;

					// Send to listener. If the listener returns false, our state will be buffered.
					if (listener.onFrameStart(workingImageData.width, workingImageData.height, workingImageData.timestamp, workingImageData.frameBytesCount)) {
						workingImageData.state = ImageDataState.EMPTY;
					}

					break;

				case ImageSchema.MSG_FRAMECHUNK_2:

					assert workingImageData.state == ImageDataState.EMPTY;

					// Calculate row length.
					int rowLength = PipeReader.readBytesLength(inputPipe, ImageSchema.MSG_FRAMECHUNK_2_FIELD_ROWBYTES_102);

					// Prepare array if not already ready.
					if (workingImageData.currentRowBytes == null || workingImageData.currentRowBytes.length != rowLength) {
						workingImageData.currentRowBytes = new byte[rowLength];
					}

					// Read bytes into array.
					PipeReader.readBytes(inputPipe, ImageSchema.MSG_FRAMECHUNK_2_FIELD_ROWBYTES_102, workingImageData.currentRowBytes, 0);
					workingImageData.state = ImageDataState.FRAME_ROW_BUFFERED;

					// Send to listener. If the listener returns false, our state will be buffered.
					if (listener.onFrameRow(workingImageData.currentRowBytes)) {
						workingImageData.state = ImageDataState.EMPTY;
					}

					break;

				case -1:
					//requestShutdown();
					break;
			}
			PipeReader.releaseReadLock(inputPipe);
		}
	}
    

    protected void consumeResponseMessage(Object listener, Pipe<GroveResponseSchema> p) {
        while (PipeReader.tryReadFragment(p)) {                
            
            int msgIdx = PipeReader.getMsgIdx(p);
            switch (msgIdx) {   

                case GroveResponseSchema.MSG_ANALOGSAMPLE_30:
                    if (listener instanceof AnalogListenerBase) {                        
                        commonAnalogEventProcessing(Port.ANALOGS[0x03&PipeReader.readInt(p, GroveResponseSchema.MSG_ANALOGSAMPLE_30_FIELD_CONNECTOR_31)],
                        				            PipeReader.readLong(p, GroveResponseSchema.MSG_ANALOGSAMPLE_30_FIELD_TIME_11), 
                        				            PipeReader.readInt(p, GroveResponseSchema.MSG_ANALOGSAMPLE_30_FIELD_VALUE_32), 
                        				            (AnalogListenerBase)listener);
                        
                    }   
                break;               
                case GroveResponseSchema.MSG_DIGITALSAMPLE_20:
                    
                    if (listener instanceof DigitalListenerBase) {
                        commonDigitalEventProcessing(Port.DIGITALS[PipeReader.readInt(p, GroveResponseSchema.MSG_DIGITALSAMPLE_20_FIELD_CONNECTOR_21)], 
                        		                     PipeReader.readLong(p, GroveResponseSchema.MSG_DIGITALSAMPLE_20_FIELD_TIME_11), 
                        		                     PipeReader.readInt(p, GroveResponseSchema.MSG_DIGITALSAMPLE_20_FIELD_VALUE_22), 
                        		                     (DigitalListenerBase)listener);
                    }   
                break; 
                case GroveResponseSchema.MSG_ENCODER_70:
                    
                    if (listener instanceof RotaryListenerBase) {    
                        int connector = PipeReader.readInt(p, GroveResponseSchema.MSG_ENCODER_70_FIELD_CONNECTOR_71);
                        long time = PipeReader.readLong(p, GroveResponseSchema.MSG_ENCODER_70_FIELD_TIME_11);
                        int value = PipeReader.readInt(p, GroveResponseSchema.MSG_ENCODER_70_FIELD_VALUE_72);
                        int delta = PipeReader.readInt(p, GroveResponseSchema.MSG_ENCODER_70_FIELD_DELTA_73);
                        int speed = PipeReader.readInt(p, GroveResponseSchema.MSG_ENCODER_70_FIELD_SPEED_74);
                        long duration = PipeReader.readLong(p, GroveResponseSchema.MSG_ENCODER_70_FIELD_PREVDURATION_75);
                        
                        ((RotaryListenerBase)listener).rotaryEvent(Port.DIGITALS[connector], time, value, delta, speed);
                                            
                    }   
                break;
                case -1:
                {    
                	this.realStage.requestShutdown();
                    PipeReader.releaseReadLock(p);
                    return;
                }   
                default:
                    throw new UnsupportedOperationException("Unknown id: "+msgIdx);
            }               
            
            //done reading message off pipe
            PipeReader.releaseReadLock(p);
        }
    }
    

	protected void commonI2CEventProcessing(I2CListenerBase listener, int addr, int register, long time, byte[] backing, int position, int length, int mask) {
		if (isIncluded(addr, includedI2Cs) && isNotExcluded(addr, excludedI2Cs)) {
			listener.i2cEvent(addr, register, time, backing, position, length, mask);
		}
	}
	    
    
	protected void commonDigitalEventProcessing(Port port, long time, int value, DigitalListenerBase dListener) {
		
		if (isIncluded(port, includedPorts) && isNotExcluded(port, excludedPorts)) {

			if (sendEveryDigitalValue[port.port]) {
				dListener.digitalEvent(port, time, 0==lastDigitalTimes[port.port] ? -1 : time-lastDigitalTimes[port.port], value);				
				if(value!=lastDigitalValues[port.port]){  
					lastDigitalValues[port.port] = value;
			    	lastDigitalTimes[port.port] = time;
				}
				
			} else {			
				if(value!=lastDigitalValues[port.port]){  
					dListener.digitalEvent(port, time, 0==lastDigitalTimes[port.port] ? -1 : time-lastDigitalTimes[port.port], value);
				    lastDigitalValues[port.port] = value;
				    lastDigitalTimes[port.port] = time;
				}
			}
		}
	}

	protected void commonAnalogEventProcessing(Port port, long time, int value, AnalogListenerBase aListener) {
		
		if (isIncluded(port, includedPorts) && isNotExcluded(port, excludedPorts)) {
			
			int runningValue = sendEveryAnalogValue[port.port] ? value : findStableReading(value, port.port);             
			
			int mean = runningValue;
			//logger.debug(port+" send every value "+sendEveryAnalogValue[port.port]);
			
			if (null!=rollingMovingAveragesAnalog[port.port]) {
				MAvgRollerLong.roll(rollingMovingAveragesAnalog[port.port], runningValue);                                                
				
				if (MAvgRollerLong.isValid(rollingMovingAveragesAnalog[port.port])) {
					mean = (int)MAvgRollerLong.mean(rollingMovingAveragesAnalog[port.port]);
				}
			} else {
				logger.info("warning this port "+port+" is attempting to use moving average but no moving average was set up.\n Was this port connected in declare connections?");
			}
			
			if (sendEveryAnalogValue[port.port]) {
				//set time first so this is 0 the moment it shows up
				//since we send every read we can send the age as greater and geater values as long as it does not change.
				if(runningValue!=lastAnalogValues[port.port]){ 
					lastAnalogTimes[port.port] = time;   
					lastAnalogValues[port.port] = runningValue;
				}
				aListener.analogEvent(port, time, 0==lastAnalogTimes[port.port] ? Long.MAX_VALUE : time-lastAnalogTimes[port.port], mean, runningValue);
				
			} else {								
				if(runningValue!=lastAnalogValues[port.port]){ 
										
					//the duration here is the duration of how long the previous value was held.
					aListener.analogEvent(port, time, 0==lastAnalogTimes[port.port] ? Long.MAX_VALUE : time-lastAnalogTimes[port.port], mean, runningValue);
				   
					lastAnalogValues[port.port] = runningValue;
				    lastAnalogTimes[port.port] = time;
				}
			}
		}
	}
	
	@Override
	public ListenerFilterIoT includePorts(Port ... ports) {
		if (!startupCompleted && (listener instanceof AnalogListener || listener instanceof DigitalListener)) {
			includedPorts = ports;
			return this;
		} else {
			if (startupCompleted) {
	    		throw new UnsupportedOperationException("ListenerFilters may only be set before startup is called.  Eg. the filters can not be changed at runtime.");
	    	} else {
	    		throw new UnsupportedOperationException("The Listener must be an instance of AnalogLister or DigitalListener in order to call this method.");
	    	}
		}
	}

	@Override
	public ListenerFilterIoT excludePorts(Port ... ports) {
		if (!startupCompleted && (listener instanceof AnalogListener || listener instanceof DigitalListener)) {
			excludedPorts = ports;
			return this;
		} else {
			if (startupCompleted) {
	    		throw new UnsupportedOperationException("ListenerFilters may only be set before startup is called.  Eg. the filters can not be changed at runtime.");
	    	} else {
	    		throw new UnsupportedOperationException("The Listener must be an instance of AnalogLister or DigitalListener in order to call this method.");
	    	}
		}
	}

	@Override
	public ListenerFilterIoT includeI2CConnections(int ... addresses) {
		if (!startupCompleted && listener instanceof I2CListener) {
			includedI2Cs = addresses;
			return this;
		} else {
			if (startupCompleted) {
	    		throw new UnsupportedOperationException("ListenerFilters may only be set before startup is called.  Eg. the filters can not be changed at runtime.");
	    	} else {
	    		throw new UnsupportedOperationException("The Listener must be an instance of DigitalLister in order to call this method.");
	    	}
		}
	}

	
	@Override
	public ListenerFilterIoT excludeI2CConnections(int... addresses) {
		if (!startupCompleted && listener instanceof I2CListener) {
			excludedI2Cs = addresses;
			return this;
	    } else {
	    	if (startupCompleted) {
	    		throw new UnsupportedOperationException("ListenerFilters may only be set before startup is called.  Eg. the filters can not be changed at runtime.");
	    	} else {
	    		throw new UnsupportedOperationException("The Listener must be an instance of DigitalLister in order to call this method.");
	    	}
	    }
	}
	
	private <E extends Enum<E>> long[] buildMaskArray(E[] state) {
		int maxOrdinal = findMaxOrdinal(state);
		int a = maxOrdinal >> 6;
		int b = maxOrdinal & 0x3F;		
		int longsCount = a+(b==0?0:1);
		
		long[] array = new long[longsCount+1];
				
		int i = state.length;
		while (--i>=0) {			
			int ordinal = state[i].ordinal();			
			array[ordinal>>6] |=  1L << (ordinal & 0x3F);			
		}
		return array;
	}

	private <E extends Enum<E>> int findMaxOrdinal(E[] state) {
		int maxOrdinal = -1;
		int i = state.length;
		while (--i>=0) {
			maxOrdinal = Math.max(maxOrdinal, state[i].ordinal());
		}
		return maxOrdinal;
	}

    
    
}
