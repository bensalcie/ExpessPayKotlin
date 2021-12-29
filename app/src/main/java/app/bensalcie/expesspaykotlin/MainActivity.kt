package app.bensalcie.expesspaykotlin

import android.app.ProgressDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import bensalcie.payhero.mpesa.mpesa.model.AccessToken
import bensalcie.payhero.mpesa.mpesa.model.STKPush
import bensalcie.payhero.mpesa.mpesa.model.STKResponse
import bensalcie.payhero.mpesa.mpesa.services.DarajaApiClient
import bensalcie.payhero.mpesa.mpesa.services.Environment
import bensalcie.payhero.mpesa.mpesa.services.Utils
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response




class MainActivity : AppCompatActivity(), View.OnClickListener, MpesaListener {
    var mAmount: EditText? = null

    var mPhone: EditText? = null
    private var mProgressDialog: ProgressDialog? = null
    private var mApiClient: DarajaApiClient? = null
    companion object {
        lateinit var mpesaListener: MpesaListener
    }

    var mPay: Button? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mAmount= findViewById(R.id.etAmount)
        mPhone = findViewById(R.id.etPhone)
        mPay = findViewById(R.id.btnPay)

        mProgressDialog = ProgressDialog(this)
        mProgressDialog!!.setTitle("Processing payment")
        mProgressDialog!!.setMessage("We are finishing up payments...")
        mApiClient = DarajaApiClient(
            "tENEMaFsLNKzAMRycuyPmd02ABsCv8IU",
            "hPHKZVS5VJCapkQf",
            Environment.SANDBOX
        )
        mApiClient!!.setIsDebug(true) //Set True to enable logging, false to disable.
        mPay!!.setOnClickListener(this)
        getAccessToken()
//        FirebaseApp.initializeApp(this)
        mpesaListener = this

    }
    override fun onClick(v: View?) {

        if (v === mPay) {
            val phoneNumber = mPhone!!.text.toString()
            val amount = mAmount!!.text.toString()


            performSTKPush( amount,phoneNumber)
        }
    }
    private fun getAccessToken() {
        mApiClient!!.setGetAccessToken(true)
        mApiClient!!.mpesaService()!!.getAccessToken().enqueue(object : Callback<AccessToken?> {
            override fun onResponse(call: Call<AccessToken?>, response: Response<AccessToken?>) {
                if (response.isSuccessful) {
                    mApiClient!!.setAuthToken(response.body()?.accessToken)
                }
            }

            override fun onFailure(call: Call<AccessToken?>, t: Throwable) {}
        })
    }
    private fun performSTKPush(amount: String, phone_number: String) {
        mProgressDialog!!.show()
        //Handle progresss here
        //credentials here are test credentials


        val timestamp = Utils.getTimestamp()
        val stkPush = STKPush(
            "PAY TO CHURCH",
            amount,
            "174379",
            "https://us-central1-dev-apps-6f6e8.cloudfunctions.net/api/myCallbackUrl",
            Utils.sanitizePhoneNumber(phone_number)!!,
            "174379",
            Utils.getPassword(
                "174379",
                "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919", timestamp!!
            )!!,
            Utils.sanitizePhoneNumber(phone_number)!!,
            timestamp,
            "Trans. desc",
            Environment.TransactionType.CustomerPayBillOnline
        )
        mApiClient!!.setGetAccessToken(false)
        mApiClient!!.mpesaService()!!.sendPush(stkPush).enqueue(object : Callback<STKResponse> {
            override fun onResponse(call: Call<STKResponse>, response: Response<STKResponse>) {
                try {
                    if (response.isSuccessful) {
                        if (response.body() != null) {
                            val stkResponse = response.body()
                            Toast.makeText(
                                this@MainActivity,
                                "Checkout Request Id: ${stkResponse?.checkoutRequestID}",
                                Toast.LENGTH_SHORT
                            ).show()
                            FirebaseMessaging.getInstance()
                                .subscribeToTopic(stkResponse?.checkoutRequestID.toString()).addOnCompleteListener {
                                    if (it.isSuccessful){
                                        Log.d("PAYMENTS",
                                            "onResponse: Subscribed successfully  : "
                                        )
                                    }else{
                                        Log.d("PAYMENTS",
                                            "onResponse: Subscribed unsuccessfull  : ${it.exception?.message}"
                                        )
                                    }
                                }
                            Log.d("PAYMENTS",
                                "onResponse: Time to process, confirm etc : " + response.body()!!
                                    .component1()
                            );
                            //handle response here
                            //response contains CheckoutRequestID,CustomerMessage,MerchantRequestID,ResponseCode,ResponseDescription
                        }

                    } else {
                        //Timber.e("Response %s", response.errorBody()!!.string())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(call: Call<STKResponse>, t: Throwable) {
                //mProgressDialog!!.dismiss()
                //Timber.e(t)
            }
        })
    }

    override fun sendSuccesfull(amount: String, phone: String, date: String, receipt: String) {
        mProgressDialog!!.dismiss()

        runOnUiThread {
            Toast.makeText(
                this, "Payment Succesfull\n" +
                        "Receipt: $receipt\n" +
                        "Date: $date\n" +
                        "Phone: $phone\n" +
                        "Amount: $amount", Toast.LENGTH_LONG
            ).show()

        }
    }

    override fun sendFailed(reason: String) {
        mProgressDialog!!.dismiss()
        runOnUiThread {
            Toast.makeText(
                this, "Payment Failed\n" +
                        "Reason: $reason"
                , Toast.LENGTH_LONG
            ).show()
        }
    }
}