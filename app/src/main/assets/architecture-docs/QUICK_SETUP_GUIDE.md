# ElmerSyncd Setup Guide

## Quick Start

### Option 1: Run from Command Line (Recommended)

1. **Add ElmerSyncd to your Windows PATH:**
   - Press `Win + X` and select "System"
   - Click "Advanced system settings"
   - Click "Environment Variables..."
   - Under "User variables", click "New..."
   - Variable name: `PATH`
   - Variable value: `C:\Program Files\ElmerSyncd`
   - Click OK and close all dialogs

2. **Open Command Prompt or PowerShell**

3. **Navigate to your sync folder:**
   ```
   cd C:\path\to\your\sync\folder
   ```

4. **Start ElmerSyncd:**
   ```
   elmer-syncd --allow-lan
   ```

5. **Connect your Android app to the host IP address and port 12345**

### Option 2: Using INI Configuration File

Edit `C:\Program Files\ElmerSyncd\elmer-syncd.ini` and add:
```
root=C:\path\to\your\sync\folder
```

Then run:
```
elmer-syncd --allow-lan
```

## Finding Your LAN IP Address

Run this command to see your available LAN IP addresses:
```
elmer-syncd --show-ip
```

## Command Line Options

- `--allow-lan` - Allow connections from LAN devices (required for Android)
- `--show-ip` - Display your LAN IP addresses and exit
- `--root <path>` - Specify the sync folder (overrides INI file)
- `--port <number>` - Change port (default: 12345)
- `--token <token>` - Set an API token for security

## Console Commands

Once ElmerSyncd is running, you can press:
- `H` - Show help menu

## Troubleshooting

**Android app won't connect:**
- Make sure you're using `--allow-lan` flag
- Check that the host IP and port (12345) are correct
- Verify both devices are on the same network

**Wrong folder is being synced:**
- Use Option 1: Run from your desired folder
- Or use Option 2: Edit the INI file with the correct path
