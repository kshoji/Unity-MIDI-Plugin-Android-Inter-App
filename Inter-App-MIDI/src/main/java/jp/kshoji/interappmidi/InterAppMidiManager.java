package jp.kshoji.interappmidi;

import android.content.Context;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.RequiresApi;

import com.unity3d.player.UnityPlayer;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Inter-App MIDI Plugin for Unity
 */
public class InterAppMidiManager {
    private MidiManager midiManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Map<String, MidiInputPort> inputPortMap = new HashMap<>();
    private final Map<String, InterAppMidiReceiver> receiverMap = new HashMap<>();
    private final Map<String, MidiOutputPort> outputPortMap = new HashMap<>();
    private final Map<MidiDeviceInfo, MidiDevice> openedDeviceMap = new HashMap<>();
    private final Map<String, String> deviceNameMap = new HashMap<>();
    private final Map<String, String> productIdMap = new HashMap<>();
    private final Map<String, String> vendorIdMap = new HashMap<>();
    private Thread connectionWatcher;
    private volatile boolean connectionWatcherEnabled;

    public void initialize(Context context) {
        inputPortMap.clear();
        receiverMap.clear();
        outputPortMap.clear();
        openedDeviceMap.clear();
        deviceNameMap.clear();
        productIdMap.clear();
        vendorIdMap.clear();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            midiManager = (MidiManager) context.getSystemService(Context.MIDI_SERVICE);

            if (midiManager != null) {
                connectionWatcher = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        connectionWatcherEnabled = true;
                        while (connectionWatcherEnabled) {
                            Set<MidiDeviceInfo> devices;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                devices = midiManager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM);
                            } else {
                                devices = new HashSet<>();
                                Collections.addAll(devices, midiManager.getDevices());
                            }

                            // detect opened
                            for (MidiDeviceInfo device : devices) {
                                openMidiDevice(device);
                            }

                            // detect closed
                            for (MidiDeviceInfo connectedDevice : openedDeviceMap.keySet()) {
                                if (!devices.contains(connectedDevice)) {
                                    MidiDevice removed = openedDeviceMap.remove(connectedDevice);
                                    if (removed != null) {
                                        closeMidiDevice(removed);
                                    }
                                }
                            }

                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }
                });
                connectionWatcher.start();
            }
        }
    }

    public void terminate() {
        if (midiManager != null) {
            connectionWatcherEnabled = false;
            if (connectionWatcher != null) {
                connectionWatcher.interrupt();
                connectionWatcher = null;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (MidiDeviceInfo connectedDevice : openedDeviceMap.keySet()) {
                MidiDevice removed = openedDeviceMap.remove(connectedDevice);
                if (removed != null) {
                    closeMidiDevice(removed);
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static class InterAppMidiReceiver extends MidiReceiver {
        private final String deviceId;
        private InterAppMidiReceiver(String deviceId) {
            this.deviceId = deviceId;
        }

        @Override
        public void onSend(byte[] message, int offset, int count, long timestamp) throws IOException {
            byte[] midiData = new byte[count];
            System.arraycopy(message, offset, midiData, 0, count);

            for (int i = 0; i < midiData.length;) {
                switch (midiData[i] & 0xf0) {
                    case 0x80:
                        if (midiData.length >= i + 3) {
                            String data = new StringBuilder().append(deviceId).append(",0,")
                                    .append(midiData[i] & 0xf).append(",")
                                    .append(midiData[i + 1]).append(",")
                                    .append(midiData[i + 2]).toString();
                            UnityPlayer.UnitySendMessage("MidiManager", "OnMidiNoteOff", data);
                        }
                        i += 3;
                        break;
                    case 0x90:
                        if (midiData.length >= i + 3) {
                            String data = new StringBuilder().append(deviceId).append(",0,")
                                    .append(midiData[i] & 0xf).append(",")
                                    .append(midiData[i + 1]).append(",")
                                    .append(midiData[i + 2]).toString();
                            UnityPlayer.UnitySendMessage("MidiManager", "OnMidiNoteOn", data);
                        }
                        i += 3;
                        break;
                    case 0xa0: // Polyphonic Aftertouch
                        if (midiData.length >= i + 3) {
                            String data = new StringBuilder().append(deviceId).append(",0,")
                                    .append(midiData[i] & 0xf).append(",")
                                    .append(midiData[i + 1]).append(",")
                                    .append(midiData[i + 2]).toString();
                            UnityPlayer.UnitySendMessage("MidiManager", "OnMidiPolyphonicAftertouch", data);
                        }
                        i += 3;
                        break;
                    case 0xb0: // Control Change
                        if (midiData.length >= i + 3) {
                            String data = new StringBuilder().append(deviceId).append(",0,")
                                    .append(midiData[i] & 0xf).append(",")
                                    .append(midiData[i + 1]).append(",")
                                    .append(midiData[i + 2]).toString();
                            UnityPlayer.UnitySendMessage("MidiManager", "OnMidiControlChange", data);
                        }
                        i += 3;
                        break;
                    case 0xc0: // Program Change
                        if (midiData.length >= i + 2) {
                            String data = new StringBuilder().append(deviceId).append(",0,")
                                    .append(midiData[i] & 0xf).append(",")
                                    .append(midiData[i + 1]).toString();
                            UnityPlayer.UnitySendMessage("MidiManager", "OnMidiProgramChange", data);
                        }
                        i += 2;
                        break;
                    case 0xd0: // Channel Aftertouch
                        if (midiData.length >= i + 2) {
                            String data = new StringBuilder().append(deviceId).append(",0,")
                                    .append(midiData[i] & 0xf).append(",")
                                    .append(midiData[i + 1]).toString();
                            UnityPlayer.UnitySendMessage("MidiManager", "OnMidiChannelAftertouch", data);
                        }
                        i += 2;
                        break;
                    case 0xe0: // Pitch Wheel
                        if (midiData.length >= i + 3) {
                            String data = new StringBuilder().append(deviceId).append(",0,")
                                    .append(midiData[i] & 0xf).append(",")
                                    .append(midiData[i + 1] | (midiData[i + 2] << 7)).toString();
                            UnityPlayer.UnitySendMessage("MidiManager", "OnMidiPitchWheel", data);
                        }
                        i += 3;
                        break;
                    case 0xf0:
                        switch (midiData[i] & 0xff) {
                            case 0xf0: // Sysex
                            {
                                StringBuilder stringBuffer = new StringBuilder();
                                stringBuffer.append(deviceId);
                                stringBuffer.append(",0");
                                boolean hasNextData = false;
                                for (int j = i; j < midiData.length; j++) {
                                    stringBuffer.append(",");
                                    stringBuffer.append(midiData[j]);
                                    if ((midiData[j] & 0xff) == 0xf7) {
                                        i += j;
                                        hasNextData = true;
                                        break;
                                    }
                                }
                                UnityPlayer.UnitySendMessage("MidiManager", "OnMidiSystemExclusive", stringBuffer.toString());

                                if (!hasNextData) {
                                    i = midiData.length;
                                }
                            }
                            break;
                            case 0xf1: // Time Code Quarter Frame
                                if (midiData.length >= i + 2) {
                                    String data = new StringBuilder().append(deviceId).append(",0,")
                                            .append(midiData[i + 1]).toString();
                                    UnityPlayer.UnitySendMessage("MidiManager", "OnMidiTimeCodeQuarterFrame", data);
                                }
                                i += 2;
                                break;
                            case 0xf2: // Song Position Pointer
                                if (midiData.length >= i + 3) {
                                    String data = new StringBuilder().append(deviceId).append(",0,")
                                            .append(midiData[i + 1] | (midiData[i + 2] << 7)).toString();
                                    UnityPlayer.UnitySendMessage("MidiManager", "OnMidiSongPositionPointer", data);
                                }
                                i += 3;
                                break;
                            case 0xf3: // Song Select
                                if (midiData.length >= i + 2) {
                                    String data = new StringBuilder().append(deviceId).append(",0,")
                                            .append(midiData[i + 1]).toString();
                                    UnityPlayer.UnitySendMessage("MidiManager", "OnMidiSongSelect", data);
                                }
                                i += 2;
                                break;
                            case 0xf6: // Tune Request
                                UnityPlayer.UnitySendMessage("MidiManager", "OnMidiTuneRequest", new StringBuilder().append(deviceId).append(",0").toString());
                                i++;
                                break;
                            case 0xf8: // Timing Clock
                                UnityPlayer.UnitySendMessage("MidiManager", "OnMidiTimingClock", new StringBuilder().append(deviceId).append(",0").toString());
                                i++;
                                break;
                            case 0xfa: // Start
                                UnityPlayer.UnitySendMessage("MidiManager", "OnMidiStart", new StringBuilder().append(deviceId).append(",0").toString());
                                i++;
                                break;
                            case 0xfb: // Continue
                                UnityPlayer.UnitySendMessage("MidiManager", "OnMidiContinue", new StringBuilder().append(deviceId).append(",0").toString());
                                i++;
                                break;
                            case 0xfc: // Stop
                                UnityPlayer.UnitySendMessage("MidiManager", "OnMidiStop", new StringBuilder().append(deviceId).append(",0").toString());
                                i++;
                                break;
                            case 0xfe: // Active Sensing
                                UnityPlayer.UnitySendMessage("MidiManager", "OnMidiActiveSensing", new StringBuilder().append(deviceId).append(",0").toString());
                                i++;
                                break;
                            case 0xff: // Reset
                                UnityPlayer.UnitySendMessage("MidiManager", "OnMidiReset", new StringBuilder().append(deviceId).append(",0").toString());
                                i++;
                                break;

                            default:
                                i++;
                                break;
                        }
                        break;

                    default:
                        i++;
                        break;
                }
            }
        }
    }

    private static String getDeviceId(int deviceId, boolean isInput, int portId) {
        return new StringBuilder().append(isInput ? "in" : "out").append(":").append(deviceId).append("-").append(portId).toString();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void openMidiDevice(final MidiDeviceInfo device) {
        if (device.getType() == MidiDeviceInfo.TYPE_VIRTUAL) {
            if (openedDeviceMap.containsKey(device)) {
                return;
            }

            midiManager.openDevice(device, new MidiManager.OnDeviceOpenedListener() {
                @Override
                public void onDeviceOpened(MidiDevice midiDevice) {
                    openedDeviceMap.put(device, midiDevice);

                    MidiDeviceInfo midiDeviceInfo = midiDevice.getInfo();
                    Bundle properties = midiDeviceInfo.getProperties();
                    String deviceName = properties.getString(MidiDeviceInfo.PROPERTY_NAME);
                    String product = properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT);
                    String vendor = properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER);

                    int midiDeviceInfoId = midiDeviceInfo.getId();
                    for (int i = 0; i < midiDeviceInfo.getInputPortCount(); i++) {
                        // MidiInputPort: used for MIDI sending
                        String deviceId = getDeviceId(midiDeviceInfoId, false, i);
                        if (!inputPortMap.containsKey(deviceId)) {
                            MidiInputPort midiInputPort = midiDevice.openInputPort(i);
                            if (midiInputPort != null) {
                                inputPortMap.put(deviceId, midiInputPort);
                                if (deviceName != null) {
                                    deviceNameMap.put(deviceId, deviceName);
                                }
                                if (product != null) {
                                    productIdMap.put(deviceId, product);
                                }
                                if (vendor != null) {
                                    vendorIdMap.put(deviceId, vendor);
                                }
                                UnityPlayer.UnitySendMessage("MidiManager", "OnMidiOutputDeviceAttached", deviceId);
                            }
                        }
                    }

                    for (int i = 0; i < midiDeviceInfo.getOutputPortCount(); i++) {
                        // MidiOutputPort: used for MIDI receiving
                        String deviceId = getDeviceId(midiDeviceInfoId, true, i);
                        if (!outputPortMap.containsKey(deviceId)) {
                            MidiOutputPort midiOutputPort = midiDevice.openOutputPort(i);
                            if (midiOutputPort != null) {
                                InterAppMidiReceiver receiver = new InterAppMidiReceiver(deviceId);
                                receiverMap.put(deviceId, receiver);
                                midiOutputPort.onConnect(receiver);
                                outputPortMap.put(deviceId, midiOutputPort);
                                if (deviceName != null) {
                                    deviceNameMap.put(deviceId, deviceName);
                                }
                                if (product != null) {
                                    productIdMap.put(deviceId, product);
                                }
                                if (vendor != null) {
                                    vendorIdMap.put(deviceId, vendor);
                                }
                                UnityPlayer.UnitySendMessage("MidiManager", "OnMidiInputDeviceAttached", deviceId);
                            }
                        }
                    }
                }
            }, handler);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void closeMidiDevice(final MidiDevice device) {
        MidiDeviceInfo midiDeviceInfo = device.getInfo();
        int midiDeviceInfoId = midiDeviceInfo.getId();
        for (int i = 0; i < midiDeviceInfo.getInputPortCount(); i++) {
            String deviceId = getDeviceId(midiDeviceInfoId, false, i);
            MidiInputPort inputPort = inputPortMap.remove(deviceId);
            if (inputPort != null) {
                try {
                    inputPort.close();
                } catch (IOException ignored) {
                }
            }
        }

        for (int i = 0; i < midiDeviceInfo.getOutputPortCount(); i++) {
            String deviceId = getDeviceId(midiDeviceInfoId, true, i);
            MidiOutputPort outputPort = outputPortMap.remove(deviceId);
            if (outputPort != null) {
                MidiReceiver receiver = receiverMap.remove(deviceId);
                if (receiver != null) {
                    try {
                        receiver.flush();
                    } catch (IOException ignored) {
                    }
                    outputPort.onDisconnect(receiver);
                }
                try {
                    outputPort.close();
                } catch (IOException ignored) {
                }
            }
        }

        try {
            device.close();
        } catch (IOException ignored) {
        }
    }

    public String getDeviceName(String deviceId) {
        if (deviceNameMap.containsKey(deviceId)) {
            return deviceNameMap.get(deviceId);
        }

        return null;
    }

    public String getProductId(String deviceId) {
        if (productIdMap.containsKey(deviceId)) {
            return productIdMap.get(deviceId);
        }

        return null;
    }

    public String getVendorId(String deviceId) {
        if (vendorIdMap.containsKey(deviceId)) {
            return vendorIdMap.get(deviceId);
        }

        return null;
    }

    public void sendMidiNoteOff(String deviceId, int channel, int note, int velocity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) (0x80 | (channel & 0x0f)), (byte) (note & 0x7f), (byte) (velocity & 0x7f)}, 0, 3, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiNoteOn(String deviceId, int channel, int note, int velocity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) (0x90 | (channel & 0x0f)), (byte) (note & 0x7f), (byte) (velocity & 0x7f)}, 0, 3, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiPolyphonicAftertouch(String deviceId, int channel, int note, int pressure) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) (0xa0 | (channel & 0x0f)), (byte) (note & 0x7f), (byte) (pressure & 0x7f)}, 0, 3, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiControlChange(String deviceId, int channel, int func, int value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) (0xb0 | (channel & 0x0f)), (byte) (func & 0x7f), (byte) (value & 0x7f)}, 0, 3, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiProgramChange(String deviceId, int channel, int program) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) (0xc0 | (channel & 0x0f)), (byte) (program & 0x7f)}, 0, 2, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiChannelAftertouch(String deviceId, int channel, int pressure) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) (0xd0 | (channel & 0x0f)), (byte) (pressure & 0x7f)}, 0, 2, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiPitchWheel(String deviceId, int channel, int amount) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) (0xe0 | (channel & 0x0f)), (byte) (amount & 0x7f), (byte) ((amount >> 7) & 0x7f)}, 0, 3, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiSystemExclusive(String deviceId, byte[] data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(data, 0, data.length, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiTimeCodeQuarterFrame(String deviceId, int value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) 0xf1, (byte) value}, 0, 2, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiSongPositionPointer(String deviceId, int position) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) 0xf2, (byte) (position & 0x7f), (byte) ((position >> 7) & 0x7f)}, 0, 3, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiSongSelect(String deviceId, int song) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) 0xf3, (byte) song}, 0, 2, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiTuneRequest(String deviceId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) 0xf6}, 0, 1, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiTimingClock(String deviceId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) 0xf8}, 0, 1, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiStart(String deviceId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) 0xfa}, 0, 1, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiContinue(String deviceId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) 0xfb}, 0, 1, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiStop(String deviceId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) 0xfc}, 0, 1, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiActiveSensing(String deviceId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) 0xfe}, 0, 1, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMidiReset(String deviceId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MidiInputPort midiInputPort = inputPortMap.get(deviceId);
                if (midiInputPort != null) {
                    midiInputPort.onSend(new byte[]{(byte) 0xff}, 0, 1, System.nanoTime());
                }
            } catch (IOException ignored) {
            }
        }
    }
}
