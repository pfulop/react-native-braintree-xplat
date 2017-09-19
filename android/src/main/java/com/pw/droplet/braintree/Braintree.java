package com.pw.droplet.braintree;

import java.util.Map;
import java.util.HashMap;

import com.braintreepayments.api.dropin.DropInActivity;
import com.braintreepayments.api.dropin.DropInRequest;
import com.braintreepayments.api.dropin.DropInResult;
import com.braintreepayments.api.interfaces.BraintreeCancelListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.google.gson.Gson;

import android.content.Intent;
import android.content.Context;
import android.app.Activity;

import com.braintreepayments.api.models.PaymentMethodNonce;
import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.exceptions.BraintreeError;
import com.braintreepayments.api.exceptions.ErrorWithResponse;
import com.braintreepayments.api.models.CardBuilder;
import com.braintreepayments.api.Card;
import com.braintreepayments.api.PayPal;
import com.braintreepayments.api.interfaces.PaymentMethodNonceCreatedListener;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.ReadableMap;

public class Braintree extends ReactContextBaseJavaModule implements ActivityEventListener {
  private static final int PAYMENT_REQUEST = 1706816330;
  private String token;

  private Callback successCallback;
  private Callback errorCallback;

  private Context mActivityContext;

  private BraintreeFragment mBraintreeFragment;

  public Braintree(ReactApplicationContext reactContext) {
    super(reactContext);
    reactContext.addActivityEventListener(this);
  }

  @Override
  public String getName() {
    return "Braintree";
  }

  public String getToken() {
    return this.token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  @ReactMethod
  public void setup(final String token, final Callback successCallback, final Callback errorCallback) {
    try {
      this.mBraintreeFragment = BraintreeFragment.newInstance(getCurrentActivity(), token);
      this.mBraintreeFragment.addListener(new BraintreeCancelListener() {
        @Override
        public void onCancel(int requestCode) {
          nonceErrorCallback("Canceled");
        }
      });
      this.mBraintreeFragment.addListener(new PaymentMethodNonceCreatedListener() {
        @Override
        public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
          nonceCallback(paymentMethodNonce.getNonce());
        }
      });
      this.mBraintreeFragment.addListener(new BraintreeErrorListener() {
        @Override
        public void onError(Exception error) {
          if (error instanceof ErrorWithResponse) {
            ErrorWithResponse errorWithResponse = (ErrorWithResponse) error;
            BraintreeError cardErrors = errorWithResponse.errorFor("creditCard");
            if (cardErrors != null) {
              Gson gson = new Gson();
              final Map<String, String> errors = new HashMap<>();
              BraintreeError numberError = cardErrors.errorFor("number");
              BraintreeError cvvError = cardErrors.errorFor("cvv");
              BraintreeError expirationDateError = cardErrors.errorFor("expirationDate");
              BraintreeError expirationYearError = cardErrors.errorFor("expirationYear");

              if (numberError != null) {
                errors.put("card_number", numberError.getMessage());
              }

              if (cvvError != null) {
                errors.put("cvv", cvvError.getMessage());
              }

              if (expirationDateError != null) {
                errors.put("expiration_date", expirationDateError.getMessage());
              }

              if (expirationYearError != null) {
                errors.put("expiration_year", expirationYearError.getMessage());
              }

              nonceErrorCallback(gson.toJson(errors));
            } else {
              nonceErrorCallback(errorWithResponse.getErrorResponse());
            }
          }
        }
      });
      this.setToken(token);
      successCallback.invoke(this.getToken());
    } catch (InvalidArgumentException e) {
      errorCallback.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void getCardNonce(final String cardNumber, final String expirationMonth, final String expirationYear, final String cvv, final Callback successCallback, final Callback errorCallback) {
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;

    CardBuilder cardBuilder = new CardBuilder()
      .cardNumber(cardNumber)
      .expirationMonth(expirationMonth)
      .expirationYear(expirationYear)
      .cvv(cvv)
      .validate(true);

    Card.tokenize(this.mBraintreeFragment, cardBuilder);
  }

  public void nonceCallback(String nonce) {
    this.successCallback.invoke(nonce);
  }

  public void nonceErrorCallback(String error) {
    this.errorCallback.invoke(error);
  }

  @ReactMethod
  public void paymentRequest(final ReadableMap options, final Callback successCallback, final Callback errorCallback) {
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;
    DropInRequest paymentRequest = new DropInRequest()
      .clientToken(this.getToken());

    (getCurrentActivity()).startActivityForResult(
      paymentRequest.getIntent(getCurrentActivity()),
      PAYMENT_REQUEST
    );
  }

  @ReactMethod
  public void paypalRequest(final Callback successCallback, final Callback errorCallback) {
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;
    PayPal.authorizeAccount(this.mBraintreeFragment);
  }

  @Override
  public void onActivityResult(Activity activity, final int requestCode, final int resultCode, final Intent data) {
    WritableMap errorData = Arguments.createMap();
    try {
      if (requestCode == PAYMENT_REQUEST) {
        switch (resultCode) {
          case Activity.RESULT_OK:
            errorData = null;
            DropInResult paymentMethodNonce = data.getParcelableExtra(
                    DropInResult.EXTRA_DROP_IN_RESULT
            );
            this.successCallback.invoke(paymentMethodNonce.getPaymentMethodNonce().getNonce());
            break;
          case Activity.RESULT_CANCELED:
            errorData.putString("Error","Cancel");
            this.errorCallback.invoke(
                   errorData
            );
            break;
          default:
            this.errorCallback.invoke(
                    data.getSerializableExtra(DropInActivity.EXTRA_ERROR)
            );
            break;
        }
      }
    } catch (Exception e){
      errorData.putString("Error","Huge error");
      this.errorCallback.invoke(
             errorData
      );
    }
  }

  public void onNewIntent(Intent intent){}

}
