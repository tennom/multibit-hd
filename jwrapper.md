# Notes on JWrapper

## Pros

* Seamless installation targeting all platforms natively
* Free and unrestricted use
* Huge compression of JVMs leading to 16Mb delivery
* Potential for continuous update on startup

## Cons

* Splash screen is JWrapper branded requiring expensive fee to remove
* OSX builds are troublesome
* JVM packaging is custom and therefore subject to attack
* Difficult to get build environment working smoothly (no Maven integration)
* Lots of quirks in the XML configuration file

## How to build

1. Make `jwrapper` directory at same level as `multibit-hd` (sibling)
2. Copy the latest `jwrapper.jar` into the directory
3. Copy the latest `JRE1-7` from JWrapper site into the directory
4. From a terminal execute the following:

```
java -Xmx512m -jar jwrapper-000something.jar ../multibit-hd/mbhd-jwrapper.xml
```

5. Wait while JREs are updated and compressed
6. Final artifacts are placed in `jwrapper/build` directory for all major platforms
