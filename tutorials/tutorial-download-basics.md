# Queuing Downloads and Tracking Download Events

This library implements a queuing system that handles the download flow, preventing an unsafe number of concurrent downloads at the same. As soon as there is a slot available, the download will start.
In order to queue a download, use the **EMPDownloadProvider** class:

```java
EmpAsset asset = new EmpAsset();
asset.assetId = "MY_ASSET_ID";

EMPDownloadProvider.getInstance().add(asset);
```

To track the status of each download, it is possible to query the **EMPDownloadProvider**:

```java
ArrayList<IDownload> completed = EMPDownloadProvider.getInstance().getDownloads(DownloadItem.State.COMPLETED);
```

For dowonloads that are ongoing, it is useful to receive regular updates of progress. It is possible to do so by registering a listener that implements interface **IDownloadEventListener**.
Other methods are available in the **IDownload** interface. Check the documentation or the reference app for more information.

```java
ArrayList<IDownload> downloads = EMPDownloadProvider.getInstance().getDownloads();
IDownload item = downloads.get(0);
item.addEventListener(new IDownloadEventListener() {
            @Override
            public void onStart() {
               
            }

            @Override
            public void onProgressUpdate(final double progress) {

            }

            @Override
            public void onStop() {

            }

            @Override
            public void onPause() {

            }

            @Override
            public void onResume() {

            }

            @Override
            public void onEntitlement(Entitlement entitlement) {

            }

            @Override
            public void onError(int errorCode, String errorMessage) {

            }

            @Override
            public void onSuccess() {
               
            }
});		
```


**NOTE:** in order for the entitlement to be loaded from the backend, the **ApiUrl**, **CustomerId** and **BusinessId** need to be set when the Application is created. 
Also, the user has to be logged in. For authentication flows, please read Exposure library tutorials [here](https://github.com/EricssonBroadcastServices/AndroidClientExposure/tree/master/tutorials) or check our reference app [here](https://github.com/EricssonBroadcastServices/AndroidClientReferenceApp). 

```java

// ...
import net.ericsson.emovs.utilities.ContextRegistry;
import net.ericsson.emovs.exposure.auth.EMPAuthProviderWithStorage;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        EMPRegistry.bindApplicationContext(this);
        EMPRegistry.bindExposureContext(Constants.API_URL, Constants.CUSTOMER, Constants.BUSSINESS_UNIT);
		// ...
	}
	
	// ...
}
```