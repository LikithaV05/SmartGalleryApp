-------------SmartGalleryApp----------

SmartGalleryApp is an Android application that helps users manage and organize their photos and videos in a more structured way. It introduces a simple workflow that allows users to track viewed media, review items later, and keep their gallery clean by archiving or removing unnecessary content.

-------------Overview----------

The app loads media directly from the device and maintains a consistent view across all screens. Media is marked as watched only when it is opened, allowing users to gradually organize their collection instead of making immediate decisions.

----------Features--------------

Loads images and videos from device storage  
Tracks media as watched and unwatched  
Provides a full screen viewer for browsing  
Supports selecting multiple items  
Allows moving media to review, archive, or trash  
Maintains separate sections for library, review, vault, and trash  

------------Workflow-------------

The app follows a simple lifecycle:

Watch → Review → Decide → Archive or Trash

Media starts as unwatched. Once viewed, it becomes watched and can be reviewed later.

----Technology used------------

Kotlin  
Jetpack Compose  
Android SDK  
MediaStore API  

-----------Installation--------------

To install using APK:
Download the APK file from the artifacts folder  
Transfer it to your Android device  
Open the file and install the application  

To run from source:
git clone https://github.com/LikithaV05/SmartGalleryApp.git  
cd SmartGalleryApp  

Open the project in Android Studio and run it on a device or emulator.

Usage:
Open the Library to view all media  
Tap a file to open it in full screen  
Select items to perform actions such as review, archive, or trash  
Archived items are moved to the vault and removed from the main library  

Permissions:
The app requires access to media files:
Android 13 and above: photos and videos permission  
Android 10 to 12: storage permission  
If permission is denied, it can be enabled from device settings.


