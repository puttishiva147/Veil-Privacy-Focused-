package com.veil.privacy.licensing
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
object LicenseRepository {
 suspend fun activate(context:Context,code:String)=withContext(Dispatchers.IO){require(code.matches(Regex("\\d{12}"))){"Enter the complete 12-digit activation code."};val result=LicenseApi.activate(code);LicenseTokenStore.save(context,result.accessToken,code,result.expiresInSeconds)}
 /** Online revalidation is required. Failure is closed: no local premium bypass. */
 suspend fun verifyAndRefresh(context:Context):Boolean=withContext(Dispatchers.IO){val token=LicenseTokenStore.token(context);val code=LicenseTokenStore.activationCode(context);try{if(!token.isNullOrBlank()){val refreshed=LicenseApi.refresh(token);LicenseTokenStore.save(context,refreshed.accessToken,code.orEmpty(),refreshed.expiresInSeconds);true}else if(!code.isNullOrBlank()){val renewed=LicenseApi.activate(code);LicenseTokenStore.save(context,renewed.accessToken,code,renewed.expiresInSeconds);true}else false}catch(_:Exception){false}}
 suspend fun activateNewCode(context:Context,code:String)=withContext(Dispatchers.IO){require(code.matches(Regex("\\d{12}"))){"Enter the complete 12-digit activation code."};val result=LicenseApi.activate(code);LicenseTokenStore.save(context,result.accessToken,code,result.expiresInSeconds)}
}
