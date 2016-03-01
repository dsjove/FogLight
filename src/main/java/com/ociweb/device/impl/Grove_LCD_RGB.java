package com.ociweb.device.impl;

import com.ociweb.device.grove.grovepi.GrovePiI2CStage;

public class Grove_LCD_RGB {

 // Device I2C Adress (note this only uses the lower 7 bits)
    public static int LCD_ADDRESS  =   (0x7c>>1); //  11 1110  0x3E
    public static final int RGB_ADDRESS  =   (0xc4>>1); // 110 0010  0x62


    // color define 
    public static final int WHITE       =    0;
    public static final int RED         =    1;
    public static final int GREEN       =    2;
    public static final int BLUE        =    3;

    public static final int REG_RED     =    0x04;        // pwm2
    public static final int REG_GREEN   =    0x03;        // pwm1
    public static final int REG_BLUE    =    0x02;        // pwm0

    public static final int REG_MODE1    =   0x00;
    public static final int REG_MODE2    =   0x01;
    public static final int REG_OUTPUT   =   0x08;

    // commands
    public static final int LCD_CLEARDISPLAY   =0x01;
    public static final int LCD_RETURNHOME     =0x02;
    public static final int LCD_ENTRYMODESET   =0x04;
    public static final int LCD_DISPLAYCONTROL =0x08;
    public static final int LCD_CURSORSHIFT    =0x10;
    public static final int LCD_FUNCTIONSET    =0x20;
    public static final int LCD_SETCGRAMADDR   =0x40;
    public static final int LCD_SETDDRAMADDR   =0x80;

    // flags for display entry mode
    public static final int LCD_ENTRYRIGHT          =0x00;
    public static final int LCD_ENTRYLEFT           =0x02;
    public static final int LCD_ENTRYSHIFTINCREMENT =0x01;
    public static final int LCD_ENTRYSHIFTDECREMENT =0x00;

    // flags for display on/off control
    public static final int LCD_DISPLAYON  =0x04;
    public static final int LCD_DISPLAYOFF =0x00;
    public static final int LCD_CURSORON   =0x02;
    public static final int LCD_CURSOROFF  =0x00;
    public static final int LCD_BLINKON    =0x01;
    public static final int LCD_BLINKOFF   =0x00;

    // flags for display/cursor shift
    public static final int LCD_DISPLAYMOVE =0x08;
    public static final int LCD_CURSORMOVE  =0x00;
    public static final int LCD_MOVERIGHT   =0x04;
    public static final int LCD_MOVELEFT    =0x00;

    // flags for function set
    public static final int LCD_8BITMODE =0x10;
    public static final int LCD_4BITMODE =0x00;
    public static final int LCD_2LINE =0x08;
    public static final int LCD_1LINE =0x00;
    public static final int LCD_5x10DOTS =0x04;
    public static final int LCD_5x8DOTS =0x00;

   /**
    * Creates a complete byte array that will set the color of a Grove RGB LCD
    * display when passed to a {@link com.ociweb.pronghorn.stage.test.ByteArrayProducerStage}
    * which is using a chunk size of {3,3,3, 3,3,3} and is being piped to a
    * {@link GrovePiI2CStage}.
    *
    * @param r 0-255 value for the Red color.
    * @param g 0-255 value for the Green color.
    * @param b 0-255 value for the Blue color.
    *
    * @return Formatted byte array which can be passed directly to a
    *         {@link com.ociweb.pronghorn.stage.test.ByteArrayProducerStage}.
    */
    public static final byte[] commandForColor(byte r, byte g, byte b) {
        return new byte[]{
            (byte) ((Grove_LCD_RGB.RGB_ADDRESS<<1)|0), (byte) 0, (byte) 0,
            (byte) ((Grove_LCD_RGB.RGB_ADDRESS<<1)|0), (byte) 1, (byte) 0,
            (byte) ((Grove_LCD_RGB.RGB_ADDRESS<<1)|0), (byte) 0x08, (byte) 0xaa,
            (byte) ((Grove_LCD_RGB.RGB_ADDRESS<<1)|0), (byte) 4, r,
            (byte) ((Grove_LCD_RGB.RGB_ADDRESS<<1)|0), (byte) 3, g,
            (byte) ((Grove_LCD_RGB.RGB_ADDRESS<<1)|0), (byte) 2, b
        };
    }
}
