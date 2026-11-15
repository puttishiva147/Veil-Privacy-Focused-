package com.veil.privacy.licensing
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
object LicenseRepository {
 suspend fun activate(context:Context,code:String)=withContext(Dispatchers.IO){require(code.matches(Regex("\\d{12}"))){"Enter the complete 12-digit activation code."};val result=LicenseApi.activate(context,code);LicenseTokenStore.save(context,result.accessToken,code,result.expiresInSeconds)}
 /** Online revalidation is required. A failed token refresh falls back to re-proving possession of the stored code. */
 suspend fun verifyAndRefresh(context:Context):Boolean=withContext(Dispatchers.IO){
  val token=LicenseTokenStore.token(context);val code=LicenseTokenStore.activationCode(context)
  if(!token.isNullOrBlank()){
   try{val refreshed=LicenseApi.refresh(token);LicenseTokenStore.save(context,refreshed.accessToken,code.orEmpty(),refreshed.expiresInSeconds);return@withContext true}catch(_:Exception){LicenseTokenStore.clearToken(context)}
  }
  if(!code.isNullOrBlank()){
   try{val renewed=LicenseApi.activate(context,code);LicenseTokenStore.save(context,renewed.accessToken,code,renewed.expiresInSeconds);return@withContext true}catch(_:Exception){LicenseTokenStore.clearToken(context)}
  }
  false
 }
 suspend fun activateNewCode(context:Context,code:String)=withContext(Dispatchers.IO){require(code.matches(Regex("\\d{12}"))){"Enter the complete 12-digit activation code."};val result=LicenseApi.activate(context,code);LicenseTokenStore.save(context,result.accessToken,code,result.expiresInSeconds)}
}
