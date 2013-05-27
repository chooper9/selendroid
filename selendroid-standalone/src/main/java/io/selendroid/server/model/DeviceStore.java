/*
 * Copyright 2013 selendroid committers.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.selendroid.server.model;

import io.selendroid.SelendroidCapabilities;
import io.selendroid.android.AndroidDevice;
import io.selendroid.android.AndroidEmulator;
import io.selendroid.device.DeviceTargetPlatform;
import io.selendroid.exceptions.AndroidDeviceException;
import io.selendroid.exceptions.DeviceStoreException;
import io.selendroid.exceptions.SelendroidException;
import io.selendroid.server.model.impl.DefaultPortFinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class DeviceStore {
  private static final Logger log = Logger.getLogger(DeviceStore.class.getName());
  private List<AndroidDevice> devicesInUse = new ArrayList<AndroidDevice>();
  private Map<DeviceTargetPlatform, List<AndroidDevice>> androidDevices =
      new HashMap<DeviceTargetPlatform, List<AndroidDevice>>();
  private EmulatorPortFinder androidEmulatorPortFinder = null;

  public DeviceStore() {
    androidEmulatorPortFinder = new DefaultPortFinder();
  }

  public DeviceStore(EmulatorPortFinder androidEmulatorPortFinder) {
    this.androidEmulatorPortFinder = androidEmulatorPortFinder;
  }

  public Integer nextEmulatorPort() {
    return androidEmulatorPortFinder.next();
  }

  /**
   * After a test session a device should be released. That means id will be removed from the list
   * of devices in use and in case of an emulator it will be stopped.
   * 
   * @param device The device to release
   * @throws AndroidDeviceException
   * @see {@link #findAndroidDevice(SelendroidCapabilities)}
   */
  public void release(AndroidDevice device) throws AndroidDeviceException {
    if (devicesInUse.contains(device)) {
      if (device instanceof AndroidEmulator) {
        AndroidEmulator emulator = (AndroidEmulator) device;
        emulator.stop();
        androidEmulatorPortFinder.release(emulator.getPort());
      }
      devicesInUse.remove(device);
    }
  }

  public void addDevices(List<AndroidDevice> androidDevices) throws AndroidDeviceException {
    if (androidDevices == null || androidDevices.isEmpty()) {
      log.info("No Android devices were found.");
      return;
    }
    for (AndroidDevice device : androidDevices) {
      if (device.isDeviceReady() == true) {
        addAndroidEmulator(device);
      }
    }
  }

  public void addEmulators(List<AndroidEmulator> emulators) throws AndroidDeviceException {
    if (emulators == null || emulators.isEmpty()) {
      SelendroidException e =
          new SelendroidException(
              "No android virtual devices were found. Please start the android tool and create emulators.");
      log.severe("Error: " + e);
      throw e;
    }
    for (AndroidEmulator emulator : emulators) {
      if (emulator.isEmulatorStarted()) {
        log.info("Skipping emulator because it is already in use: " + emulator);
        continue;
      }
      log.info("Adding: " + emulator);
      addAndroidEmulator((AndroidDevice) emulator);
    }
    if (androidDevices.isEmpty()) {
      throw new SelendroidException("No Android virtual devices that can be used were found. "
          + "Please note that only switched off emulators can be used.");
    }
  }

  protected void addAndroidEmulator(AndroidDevice emulator) throws AndroidDeviceException {
    if (androidDevices.containsKey(emulator.getTargetPlatform())) {
      if (androidDevices.get(emulator.getTargetPlatform()) == null) {
        androidDevices.put(emulator.getTargetPlatform(), new ArrayList<AndroidDevice>());
      }
      androidDevices.get(emulator.getTargetPlatform()).add((AndroidDevice) emulator);
    } else {
      List<AndroidDevice> device = new ArrayList<AndroidDevice>();
      device.add((AndroidDevice) emulator);
      androidDevices.put(emulator.getTargetPlatform(), device);
    }
  }

  /**
   * Finds a device for the requested capabilities. <b>important note:</b> if the device is not any
   * longer used, call the {@link #release(AndroidDevice)} method.
   * 
   * @param caps The desired test session capabilities.
   * @return Matching device for a test session.
   * @throws DeviceStoreException
   * @see {@link #release(AndroidDevice)}
   */
  public synchronized AndroidDevice findAndroidDevice(SelendroidCapabilities caps)
      throws DeviceStoreException {
    if (caps == null) {
      throw new IllegalArgumentException("Error: capabilities are null");
    }
    if (androidDevices.isEmpty()) {
      throw new DeviceStoreException(
          "Fatal Error: Device Store does not contain any Android Device.");
    }
    String androidTarget = caps.getAndroidTarget();
    if (androidTarget == null || androidTarget.isEmpty()) {
      throw new DeviceStoreException("'androidTarget' is missing in desired capabilities.");
    }
    DeviceTargetPlatform platform = DeviceTargetPlatform.valueOf(androidTarget);
    if (!androidDevices.containsKey(platform)) {
      throw new DeviceStoreException(
          "Device store does not contain a device of requested platform: " + platform);
    }
    for (AndroidDevice device : androidDevices.get(platform)) {
      if (isEmulatorSwitchedOff(device) == false && device.screenSizeMatches(caps.getScreenSize())) {
        if (devicesInUse.contains(device)) {
          continue;
        }
        devicesInUse.add(device);
        return device;
      }
    }
    throw new DeviceStoreException("No devices are found. "
        + "This can happen if the devices are in use or no device screen "
        + "matches the required capabilities.");
  }

  private boolean isEmulatorSwitchedOff(AndroidDevice device) throws DeviceStoreException {
    if (device instanceof AndroidEmulator) {
      try {
        return ((AndroidEmulator) device).isEmulatorStarted();
      } catch (AndroidDeviceException e) {
        throw new DeviceStoreException(e);
      }
    }
    return false;
  }

  /**
   * For testing only
   */
  /* package */List<AndroidDevice> getDevicesInUse() {
    return devicesInUse;
  }

  /**
   * For testing only
   */
  /* package */Map<DeviceTargetPlatform, List<AndroidDevice>> getDevicesList() {
    return androidDevices;
  }
}
