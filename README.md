# NFC Reader 01

This is an Android application for reading and writing NFC tags. It is designed to interact with NFC tags that follow a specific protocol, allowing for reading and writing of identity, process, configuration, and engineering data.

## Features

* **Read and Write NFC Tags:** The application can read and write data to NFC tags using the NDEF format.
* **Dual-Scan Protocol:** It follows a dual-scan protocol for writing commands and reading responses from the NFC tag.
* **Data Parsing:** The application can parse binary data from NFC tags into structured data objects.
* **UI for Data Display:** It provides a user interface to display the parsed data in a human-readable format.
* **Logging:** The application includes a logging mechanism to record all NFC interactions for debugging purposes.

## Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/nfc-reader-01.git
   ```
2. **Open in Android Studio:**
   Open the cloned project in Android Studio.
3. **Build the project:**
   Build the project to download all the required dependencies.
4. **Run on a device with NFC:**
   Run the application on an Android device with NFC capabilities.

## Usage

1. **Approach an NFC tag:**
   Bring an NFC tag close to the device's NFC antenna.
2. **Read data:**
   The application will automatically detect the NFC tag and display its information.
3. **Write data:**
   Use the configuration screen to write new data to the NFC tag.
4. **View logs:**
   The notifications screen displays a log of all NFC interactions.
