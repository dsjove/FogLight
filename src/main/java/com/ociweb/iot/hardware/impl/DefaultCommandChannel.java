package com.ociweb.iot.hardware.impl;

import com.ociweb.iot.hardware.HardwareImpl;
import com.ociweb.iot.maker.FogCommandChannel;
import com.ociweb.iot.maker.Port;
import com.ociweb.pronghorn.iot.schema.GroveRequestSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class DefaultCommandChannel extends FogCommandChannel{


	public DefaultCommandChannel(GraphManager gm, HardwareImpl hardware, int features, 
			                    int instance, PipeConfigManager pcm) {
			super(gm, hardware, features, instance, pcm);
	}
	

	private boolean block(int connector, long duration) {
    	assert((0 != (initFeatures & PIN_WRITER))) : "CommandChannel must be created with PIN_WRITER flag";
		assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
		try {
			if (goHasRoom() &&  PipeWriter.tryWriteFragment(pinOutput, GroveRequestSchema.MSG_BLOCKCONNECTION_220)) {

				PipeWriter.writeInt(pinOutput, GroveRequestSchema.MSG_BLOCKCONNECTION_220_FIELD_CONNECTOR_111, connector);
				PipeWriter.writeLong(pinOutput, GroveRequestSchema.MSG_BLOCKCONNECTION_220_FIELD_DURATIONNANOS_13, duration*MS_TO_NS);
				
				PipeWriter.publishWrites(pinOutput);
				
				builder.releasePinOutTraffic(1,this);
                
                return true;
			} else {
				return false;
			}

		} finally {
			assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
		}
	}

	@Override
	public boolean setValue(Port port, boolean value) {
		assert(null!=port) : "port is required";
		assert((0 != (initFeatures & PIN_WRITER))) : "CommandChannel must be created with PIN_WRITER flag";
		assert(null!=builder.getConnectedDevice(port)) : "The port "+port+" was never assigned to any device in the configuration block";
		return setValue(port, (!value) ? 0 : builder.getConnectedDevice(port).range()-1);
	}
	
	@Override
	public boolean setValue(Port port, int value) {
		assert((0 != (initFeatures & PIN_WRITER))) : "CommandChannel must be created with PIN_WRITER flag";
		int mask = 0;
		int msgId;
		int msgField1;
		int msgField2;
		if (port.isAnalog()) {
			mask = ANALOG_BIT;
			msgId= GroveRequestSchema.MSG_ANALOGSET_140;
			msgField1 = GroveRequestSchema.MSG_ANALOGSET_140_FIELD_CONNECTOR_141;
			msgField2 = GroveRequestSchema.MSG_ANALOGSET_140_FIELD_VALUE_142;
		} else {
			msgId= GroveRequestSchema.MSG_DIGITALSET_110;
			msgField1 = GroveRequestSchema.MSG_DIGITALSET_110_FIELD_CONNECTOR_111;
			msgField2 = GroveRequestSchema.MSG_DIGITALSET_110_FIELD_VALUE_112;
		}
		
		assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
		try {        
			if (goHasRoom() &&  PipeWriter.tryWriteFragment(pinOutput, msgId)) {

				PipeWriter.writeInt(pinOutput, msgField1, mask|port.port);
				PipeWriter.writeInt(pinOutput, msgField2, value);
				PipeWriter.publishWrites(pinOutput);
			                
				builder.releasePinOutTraffic(1,this);
			
				
                return true;
			} else {
				return false;
			}
		} finally {
			assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
		}
	}
	

	@Override
	public boolean digitalPulse(Port port) {
	    return digitalPulse(port, 0);
	}
	@Override
	public boolean digitalPulse(Port port, long durationNanos) {
		    assert((0 != (initFeatures & PIN_WRITER))) : "CommandChannel must be created with PIN_WRITER flag";
			if (port.isAnalog()) {
				throw new UnsupportedOperationException();
			}
					
	        assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
	        try {
	            int msgCount = durationNanos > 0 ? 3 : 2;
	            
	            if (PipeWriter.hasRoomForFragmentOfSize(pinOutput, 2 * Pipe.sizeOf(i2cOutput, GroveRequestSchema.MSG_DIGITALSET_110)) && 
	            		goHasRoom() ) {           
	            
	                //Pulse on
	                if (!PipeWriter.tryWriteFragment(pinOutput, GroveRequestSchema.MSG_DIGITALSET_110)) {
	                   throw new RuntimeException("Should not have happend since the pipe was already checked.");
	                }

	                PipeWriter.writeInt(pinOutput, GroveRequestSchema.MSG_DIGITALSET_110_FIELD_CONNECTOR_111, port.port);
	                PipeWriter.writeInt(pinOutput, GroveRequestSchema.MSG_DIGITALSET_110_FIELD_VALUE_112, 1);

	                PipeWriter.publishWrites(pinOutput);
	                
	                //duration
	                //delay
	                if (durationNanos>0) {
	                    if (!PipeWriter.tryWriteFragment(pinOutput, GroveRequestSchema.MSG_BLOCKCONNECTION_220)) {
	                        throw new RuntimeException("Should not have happend since the pipe was already checked.");
	                    }
	                    PipeWriter.writeInt(pinOutput, GroveRequestSchema.MSG_BLOCKCONNECTION_220_FIELD_CONNECTOR_111, port.port);
	                    PipeWriter.writeLong(pinOutput, GroveRequestSchema.MSG_BLOCKCONNECTION_220_FIELD_DURATIONNANOS_13, durationNanos);
	                    PipeWriter.publishWrites(pinOutput);
	                }
	                

	                //Pulse off
	                if (!PipeWriter.tryWriteFragment(pinOutput, GroveRequestSchema.MSG_DIGITALSET_110)) {
	                       throw new RuntimeException("Should not have happend since the pipe was already checked.");
	                    }

                    PipeWriter.writeInt(pinOutput, GroveRequestSchema.MSG_DIGITALSET_110_FIELD_CONNECTOR_111, port.port);
                    PipeWriter.writeInt(pinOutput, GroveRequestSchema.MSG_DIGITALSET_110_FIELD_VALUE_112, 0);

                    PipeWriter.publishWrites(pinOutput);               
	                
                    builder.releasePinOutTraffic(msgCount,this);

	                return true;
	            }else{
	                return false;
	            }
	        } finally {
	            assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
	        }
	}
	

	@Override
	public boolean setValueAndBlock(Port port, boolean value, long durationMilli) {
		return setValueAndBlock(port, 
				                (!value) ? 0 : builder.getConnectedDevice(port).range()-1,
				                durationMilli);
	}


    @Override
    public boolean setValueAndBlock(Port port, int value, long msDuration) {
    	assert((0 != (initFeatures & PIN_WRITER))) : "CommandChannel must be created with PIN_WRITER flag";
    	int mask = 0;
		int msgId;
		int msgField1;
		int msgField2;
		if (port.isAnalog()) {
			mask = ANALOG_BIT;
			msgId= GroveRequestSchema.MSG_ANALOGSET_140;
			msgField1 = GroveRequestSchema.MSG_ANALOGSET_140_FIELD_CONNECTOR_141;
			msgField2 = GroveRequestSchema.MSG_ANALOGSET_140_FIELD_VALUE_142;
		} else {
			msgId= GroveRequestSchema.MSG_DIGITALSET_110;
			msgField1 = GroveRequestSchema.MSG_DIGITALSET_110_FIELD_CONNECTOR_111;
			msgField2 = GroveRequestSchema.MSG_DIGITALSET_110_FIELD_VALUE_112;
		}
		
		
        assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
        try {        
            
            if (goHasRoom() && PipeWriter.hasRoomForFragmentOfSize(pinOutput, Pipe.sizeOf(pinOutput, msgId)+
                                                                                                  Pipe.sizeOf(pinOutput, GroveRequestSchema.MSG_BLOCKCONNECTION_220)  ) ) {
            
            	
                PipeWriter.tryWriteFragment(pinOutput, msgId);
                PipeWriter.writeInt(pinOutput, msgField1, mask|port.port);
                PipeWriter.writeInt(pinOutput, msgField2, value);
                PipeWriter.publishWrites(pinOutput);
                            
                PipeWriter.tryWriteFragment(pinOutput, GroveRequestSchema.MSG_BLOCKCONNECTION_220);
                PipeWriter.writeInt(pinOutput, GroveRequestSchema.MSG_BLOCKCONNECTION_220_FIELD_CONNECTOR_111, mask|port.port);
                PipeWriter.writeLong(pinOutput, GroveRequestSchema.MSG_BLOCKCONNECTION_220_FIELD_DURATIONNANOS_13, msDuration*MS_TO_NS);
                
                PipeWriter.publishWrites(pinOutput);
                
                builder.releasePinOutTraffic(2,this);
                
                return true;
            } else {
                return false;
            }
        } finally {
            assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
        }        
    }
	


    @Override
    public boolean block(Port port, long duration) { 
    	assert((0 != (initFeatures & PIN_WRITER))) : "CommandChannel must be created with PIN_WRITER flag";
        return block((port.isAnalog()?ANALOG_BIT:0) |port.port,duration); 
    }

    @Override
    public boolean blockUntil(Port port, long time) {
    	assert((0 != (initFeatures & PIN_WRITER))) : "CommandChannel must be created with PIN_WRITER flag";
        assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
        try {
            if (goHasRoom() &&  PipeWriter.tryWriteFragment(pinOutput, GroveRequestSchema.MSG_BLOCKCONNECTIONUNTIL_221)) {

                PipeWriter.writeInt(pinOutput, GroveRequestSchema.MSG_BLOCKCONNECTIONUNTIL_221_FIELD_CONNECTOR_111, port.port);
                PipeWriter.writeLong(pinOutput, GroveRequestSchema.MSG_BLOCKCONNECTIONUNTIL_221_FIELD_TIMEMS_114, time);
                PipeWriter.publishWrites(pinOutput);
                
                int count = 1;
                builder.releasePinOutTraffic(count,this);
             
                return true;
            } else {
                return false;
            }

        } finally {
            assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
        }
    }





}
