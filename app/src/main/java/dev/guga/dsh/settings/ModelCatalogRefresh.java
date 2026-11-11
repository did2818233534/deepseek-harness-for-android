package dev.guga.dsh.settings;

import android.content.Context;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Startup refresh is independent of the settings UI and never changes enabled models. */
public final class ModelCatalogRefresh {
  private static final OkHttpClient CLIENT=new OkHttpClient.Builder().callTimeout(20,TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).build();
  public static String today(){return LocalDate.now().toString();}
  public static void onAppOpened(Context context) {
    Settings settings=new Settings(context.getApplicationContext());
    new Thread(()->{
      for(ProviderRegistry.Provider provider:ProviderRegistry.ALL) try {
        String secret=settings.apiKey(provider.id), base=settings.base(provider.id), endpoint=settings.endpoint(provider.id,"models");
        if(!settings.providerEnabled(provider.id) || secret.isEmpty() || !settings.claimCatalogRefresh(provider.id,today()))continue;
        int version=settings.beginCatalogRequest(provider.id);
        try(Response response=CLIENT.newCall(ModelEndpoint.request(provider.protocol,base,true,secret).url(endpoint).build()).execute()) {
          if(!response.isSuccessful() || response.body()==null)continue;
          String json=response.body().string();ModelList.parse(json);
          // Never overwrite a catalog after its credentials or endpoint were edited.
          synchronized(ModelCatalogRefresh.class) {
            if(settings.isCatalogRequestCurrent(provider.id,version) && base.equals(settings.base(provider.id)) && secret.equals(settings.apiKey(provider.id)) && endpoint.equals(settings.endpoint(provider.id,"models")))
              settings.saveModelCatalog(provider.id,json);
          }
        }
      }catch(Exception ignored) { /* Keep cached models; manual refresh reports failures. */ }
    },"model-catalog-refresh").start();
  }
  private ModelCatalogRefresh() {}
}
