# Edison and Raspberry Pi examples for Pronghorn #
This project contains example stages and applications for use with a Raspbery Pi or Intel Edison.

You can find the two primary example applications in com.ociweb.device.testApps.GrovePiTestApp and com.ociweb.device.testApps.GroveShieldTestApp.

## Roadmap ##
- Refactor I2C code to be device-agnostic.
- Unify and simplify interaction with the RGB LCD.
- Combine the test applications such that Pronghorn can detect the difference between a Pi and an Edison (perhaps this is just a dream...?)

## Known Issues ##
- Libjffi is broken on Raspberry Pi's, requiring a manual recompile as covered in this post [here](https://github.com/jruby/jruby/issues/1561).