package info.blockchain.wallet;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;

import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.crypto.MnemonicException;

import org.apache.commons.codec.DecoderException;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import info.blockchain.wallet.access.AccessFactory;
import info.blockchain.wallet.pairing.PairingFactory;
import info.blockchain.wallet.payload.PayloadFactory;
import info.blockchain.wallet.util.AppUtil;
import info.blockchain.wallet.util.CharSequenceX;
import info.blockchain.wallet.util.ConnectivityStatus;
import info.blockchain.wallet.util.PrefsUtil;
import info.blockchain.wallet.util.ToastCustom;
import info.blockchain.wallet.util.TypefaceUtil;
import info.blockchain.wallet.util.WebUtil;
import piuk.blockchain.android.R;

public class PinEntryActivity extends Activity {

    String userEnteredPIN = "";
    String userEnteredPINConfirm = null;

    final int PIN_LENGTH = 4;

    TextView titleView = null;

    TextView pinBox0 = null;
    TextView pinBox1 = null;
    TextView pinBox2 = null;
    TextView pinBox3 = null;

    TextView[] pinBoxArray = null;

    private ProgressDialog progress = null;

    private String strEmail    = null;
    private String strPassword = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_pin_entry);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        getWindow().getDecorView().findViewById(android.R.id.content).setFilterTouchesWhenObscured(true);

        //Coming from CreateWalletFragment
        getBundleData();
        if (strPassword != null && strEmail != null) {
            saveLoginAndPassword();
            createWallet();
        }

        // Set title state
        Typeface typeface = TypefaceUtil.getInstance(this).getRobotoTypeface();
        titleView = (TextView)findViewById(R.id.titleBox);
        titleView.setTypeface(typeface);
        if(PrefsUtil.getInstance(PinEntryActivity.this).getValue(PrefsUtil.KEY_PIN_IDENTIFIER, "").length() < 1) {

            titleView.setText(R.string.create_pin);
        }
        else {
            titleView.setText(R.string.pin_entry);
        }

        pinBox0 = (TextView)findViewById(R.id.pinBox0);
        pinBox1 = (TextView)findViewById(R.id.pinBox1);
        pinBox2 = (TextView)findViewById(R.id.pinBox2);
        pinBox3 = (TextView)findViewById(R.id.pinBox3);

        pinBoxArray = new TextView[PIN_LENGTH];
        pinBoxArray[0] = pinBox0;
        pinBoxArray[1] = pinBox1;
        pinBoxArray[2] = pinBox2;
        pinBoxArray[3] = pinBox3;

        if(!ConnectivityStatus.hasConnectivity(this)) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);

            final String message = getString(R.string.check_connectivity_exit);

            builder.setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_continue,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int id) {
                            d.dismiss();
                            Intent intent = new Intent(PinEntryActivity.this, PinEntryActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                });

            builder.create().show();
        }

        int fails = PrefsUtil.getInstance(PinEntryActivity.this).getValue(PrefsUtil.KEY_PIN_FAILS, 0);
        if(fails >= 3)	{
            ToastCustom.makeText(getApplicationContext(), getString(R.string.pin_3_strikes), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
//        	validationDialog();

            new AlertDialog.Builder(PinEntryActivity.this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.password_or_wipe)
                    .setCancelable(false)
                    .setPositiveButton(R.string.use_password, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {

                            validationDialog();

                        }
                    }).setNegativeButton(R.string.wipe_wallet, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {

                    AppUtil.getInstance(PinEntryActivity.this).clearCredentialsAndRestart();

                }
            }).show();

        }

    }

    private void saveLoginAndPassword() {
        PrefsUtil.getInstance(this).setValue(PrefsUtil.KEY_EMAIL, strEmail);
        PayloadFactory.getInstance().setEmail(strEmail);
        PayloadFactory.getInstance().setTempPassword(new CharSequenceX(strPassword));
    }

    private void createWallet() {

        try {
            // create wallet
            // restart

//            PrefsUtil.getInstance(this).setValue(PrefsUtil.KEY_HD_ISUPGRADED, true);
            AppUtil.getInstance(this).setNewlyCreated(true);

            HDPayloadBridge.getInstance(this).createHDWallet(12, "", 1);

            PayloadFactory.getInstance(this).remoteSaveThread();

            whitelistGuid();// <-- remove after beta invite system
//            AppUtil.getInstance(this).restartApp();// <-- put back after beta invite system

        } catch (IOException | MnemonicException.MnemonicLengthException e) {
            ToastCustom.makeText(getApplicationContext(), getString(R.string.hd_error), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
            AppUtil.getInstance(this).clearCredentialsAndRestart();
        }

    }

    private void whitelistGuid() {

        if(progress != null && progress.isShowing()) {
            progress.dismiss();
            progress = null;
        }
        progress = new ProgressDialog(PinEntryActivity.this);
        progress.setCancelable(false);
        progress.setTitle(R.string.app_name);
        progress.setMessage("Registering for BETA...");
        progress.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();

                String response = "";
                try {
                    StringBuilder args = new StringBuilder();
                    args.append("secret=HvWJeR1WdybHvq0316i");
                    args.append("&guid=" + PrefsUtil.getInstance(PinEntryActivity.this).getValue(PrefsUtil.KEY_GUID, ""));
                    args.append("&email=" + PrefsUtil.getInstance(PinEntryActivity.this).getValue(PrefsUtil.KEY_EMAIL, ""));

                    Log.v("", "vos postURL: " + "https://dev.blockchain.info/whitelist_guid/" + args.toString());
                    response = WebUtil.getInstance().postURL("https://dev.blockchain.info/whitelist_guid/", args.toString());
                    Log.v("", "vos response: " + response);

                    JSONObject jsonObject = new JSONObject(response);

                    if(progress != null && progress.isShowing()) {
                        progress.dismiss();
                        progress = null;
                    }

                    if(jsonObject.toString().contains("error")) {
                        String error = (String) jsonObject.get("error");
                        ToastCustom.makeText(getApplicationContext(), "Error: "+error, ToastCustom.LENGTH_LONG, ToastCustom.TYPE_ERROR);
                    }else{
                        ToastCustom.makeText(getApplicationContext(), "Success", ToastCustom.LENGTH_LONG, ToastCustom.TYPE_ERROR);
                        AppUtil.getInstance(PinEntryActivity.this).restartApp();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    if(progress != null && progress.isShowing()) {
                        progress.dismiss();
                        progress = null;
                    }
                }
                Looper.loop();
            }
        }
        ).start();
    }

    private void getBundleData() {

        Bundle extras = getIntent().getExtras();

        if (extras != null && extras.containsKey("_email")) {
            strEmail = extras.getString("_email");
        }

        if (extras != null && extras.containsKey("_pw")) {
            strPassword = extras.getString("_pw");
        }

        if (extras != null && extras.containsKey(PairingFactory.KEY_EXTRA_IS_PAIRING))
            AppUtil.getInstance(this).restartApp(); // ?
    }

    int exitClicked = 0;
    int exitCooldown = 2;//seconds
    @Override
    public void onBackPressed()
    {
        exitClicked++;
        if(exitClicked==2) {
            AppUtil.getInstance(this).setIsClosed(true);
            AppUtil.getInstance(this).clearPinEntryTime();
            finish();
        }else
            ToastCustom.makeText(this, getString(R.string.exit_confirm), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_GENERAL);

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                for(int j = 0; j <= exitCooldown; j++)
                {
                    try{Thread.sleep(1000);} catch (InterruptedException e){e.printStackTrace();}
                    if(j >= exitCooldown)exitClicked = 0;
                }
            }
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    public void validatePIN(final String PIN) {
        validatePINThread(PIN);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppUtil.getInstance(this).setIsLocked(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppUtil.getInstance(this).setIsLocked(false);
    }

    private void updatePayloadThread(final CharSequenceX pw) {

        final Handler handler = new Handler();

        if(progress != null && progress.isShowing()) {
            progress.dismiss();
            progress = null;
        }
        progress = new ProgressDialog(PinEntryActivity.this);
        progress.setCancelable(false);
        progress.setTitle(R.string.app_name);
        progress.setMessage(getString(R.string.decrypting_wallet));
        progress.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Looper.prepare();
                    if(HDPayloadBridge.getInstance(PinEntryActivity.this).init(pw)) {

                        if(AppUtil.getInstance(PinEntryActivity.this).isNewlyCreated() && (PayloadFactory.getInstance().get().getHdWallet().getAccounts().get(0).getLabel()==null || PayloadFactory.getInstance().get().getHdWallet().getAccounts().get(0).getLabel().isEmpty()))
                            PayloadFactory.getInstance().get().getHdWallet().getAccounts().get(0).setLabel(getResources().getString(R.string.default_wallet_name));

                        PayloadFactory.getInstance().setTempPassword(pw);

                        handler.post(new Runnable() {
                            @Override
                            public void run() {

                                if(progress != null && progress.isShowing()) {
                                    progress.dismiss();
                                    progress = null;
                                }

                                if(PrefsUtil.getInstance(PinEntryActivity.this).getValue(PrefsUtil.KEY_EMAIL_VERIFIED, false)){

                                    AppUtil.getInstance(PinEntryActivity.this).restartApp("verified", true);

                                }
                                else    {

                                    if(Long.parseLong(PrefsUtil.getInstance(PinEntryActivity.this).getValue(PrefsUtil.KEY_HD_UPGRADED_LAST_REMINDER, "0")) == 0L && !PayloadFactory.getInstance().get().isUpgraded())    {
                                        Intent intent = new Intent(PinEntryActivity.this, UpgradeWalletActivity.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                    }
                                    else if(PrefsUtil.getInstance(PinEntryActivity.this).getValue(PrefsUtil.KEY_EMAIL_VERIFIED, false))    {
                                        AppUtil.getInstance(PinEntryActivity.this).restartApp("verified", true);
                                    }
                                    else    {
                                        Intent intent = new Intent(PinEntryActivity.this, ConfirmationCodeActivity.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                    }
                                }

                           }

                        });

                    }
                    else {
                        ToastCustom.makeText(PinEntryActivity.this, getString(R.string.invalid_password), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                        if(progress != null && progress.isShowing()) {
                            progress.dismiss();
                            progress = null;
                        }

                        Intent intent = new Intent(PinEntryActivity.this, PinEntryActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }

                    Looper.loop();

                }
                catch(JSONException | IOException | DecoderException | AddressFormatException
                        | MnemonicException.MnemonicLengthException | MnemonicException.MnemonicWordException
                        | MnemonicException.MnemonicChecksumException e) {
                    e.printStackTrace();
                }
                finally {
                    if(progress != null && progress.isShowing()) {
                        progress.dismiss();
                        progress = null;
                    }
                }

            }
        }).start();
    }

    private void createPINThread(final String pin) {

        final Handler handler = new Handler();

        if(progress != null && progress.isShowing()) {
            progress.dismiss();
            progress = null;
        }
        progress = new ProgressDialog(PinEntryActivity.this);
        progress.setCancelable(false);
        progress.setTitle(R.string.app_name);
        progress.setMessage(getString(R.string.creating_pin));
        progress.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();

                if(AccessFactory.getInstance(PinEntryActivity.this).createPIN(PayloadFactory.getInstance().getTempPassword(), pin)) {

                    if(progress != null && progress.isShowing()) {
                        progress.dismiss();
                        progress = null;
                    }

                    PrefsUtil.getInstance(PinEntryActivity.this).setValue(PrefsUtil.KEY_PIN_FAILS, 0);
                    updatePayloadThread(PayloadFactory.getInstance().getTempPassword());

                }
                else {

                    if(progress != null && progress.isShowing()) {
                        progress.dismiss();
                        progress = null;
                    }

                    ToastCustom.makeText(PinEntryActivity.this, getString(R.string.create_pin_failed), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                    PrefsUtil.getInstance(PinEntryActivity.this).clear();
                    AppUtil.getInstance(PinEntryActivity.this).restartApp();
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        ;
                    }
                });

                Looper.loop();

            }
        }).start();
    }

    private void validatePINThread(final String pin) {

        final Handler handler = new Handler();

        if(progress != null && progress.isShowing()) {
            progress.dismiss();
            progress = null;
        }
        progress = new ProgressDialog(PinEntryActivity.this);
        progress.setCancelable(false);
        progress.setTitle(R.string.app_name);
        progress.setMessage(getString(R.string.validating_pin));
        progress.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();

                CharSequenceX password = null;

                try {
                    password = AccessFactory.getInstance(PinEntryActivity.this).validatePIN(pin);
                }catch (Exception e){
                    if(progress != null && progress.isShowing()) {
                        progress.dismiss();
                        progress = null;
                    }

                    ToastCustom.makeText(PinEntryActivity.this, getString(R.string.unexpected_error), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                    Intent intent = new Intent(PinEntryActivity.this, PinEntryActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return;
                }

                if(password != null) {

                    if(progress != null && progress.isShowing()) {
                        progress.dismiss();
                        progress = null;
                    }

                    PrefsUtil.getInstance(PinEntryActivity.this).setValue(PrefsUtil.KEY_PIN_FAILS, 0);
                    updatePayloadThread(password);
                }
                else {

                    if(progress != null && progress.isShowing()) {
                        progress.dismiss();
                        progress = null;
                    }

                    int fails = PrefsUtil.getInstance(PinEntryActivity.this).getValue(PrefsUtil.KEY_PIN_FAILS, 0);
                    PrefsUtil.getInstance(PinEntryActivity.this).setValue(PrefsUtil.KEY_PIN_FAILS, ++fails);
//		        	PrefsUtil.getInstance(PinEntryActivity.this).setValue(PrefsUtil.KEY_LOGGED_IN, false);
                    ToastCustom.makeText(PinEntryActivity.this, getString(R.string.invalid_pin), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                    Intent intent = new Intent(PinEntryActivity.this, PinEntryActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        ;
                    }
                });

                Looper.loop();

            }
        }).start();
    }

    private void validationDialog()	{

        final EditText password = new EditText(this);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        new AlertDialog.Builder(this)
        .setTitle(R.string.app_name)
        .setMessage(PinEntryActivity.this.getString(R.string.password_entry))
        .setView(password)
        .setCancelable(false)
        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

                final String pw = password.getText().toString();

                if(pw != null && pw.length() > 0) {
                    validatePasswordThread(new CharSequenceX(pw));
                }

            }
        }).show();

    }

    private void validatePasswordThread(final CharSequenceX pw) {

        final Handler handler = new Handler();

        if(progress != null && progress.isShowing()) {
            progress.dismiss();
            progress = null;
        }
        progress = new ProgressDialog(PinEntryActivity.this);
        progress.setCancelable(false);
        progress.setTitle(R.string.app_name);
        progress.setMessage(getString(R.string.validating_password));
        progress.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Looper.prepare();

                    PayloadFactory.getInstance().setTempPassword(new CharSequenceX(""));
                    if(HDPayloadBridge.getInstance(PinEntryActivity.this).init(pw)) {

                        ToastCustom.makeText(PinEntryActivity.this, getString(R.string.pin_3_strikes_password_accepted), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_OK);

                        PayloadFactory.getInstance().setTempPassword(pw);
                        PrefsUtil.getInstance(PinEntryActivity.this).removeValue(PrefsUtil.KEY_PIN_FAILS);
                        PrefsUtil.getInstance(PinEntryActivity.this).removeValue(PrefsUtil.KEY_PIN_IDENTIFIER);

                        Intent intent = new Intent(PinEntryActivity.this, PinEntryActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                    else {
                        ToastCustom.makeText(PinEntryActivity.this, getString(R.string.invalid_password), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                        if(progress != null && progress.isShowing()) {
                            progress.dismiss();
                            progress = null;
                        }

                        validationDialog();

                    }

                    Looper.loop();

                }
                catch(JSONException | IOException | DecoderException | AddressFormatException
                        | MnemonicException.MnemonicLengthException | MnemonicException.MnemonicWordException
                        | MnemonicException.MnemonicChecksumException e) {
                    e.printStackTrace();
                }
                finally {
                    if(progress != null && progress.isShowing()) {
                        progress.dismiss();
                        progress = null;
                    }
                }

            }
        }).start();
    }

    public void padClicked(View view) {

        if(userEnteredPIN.length() == PIN_LENGTH) {
            return;
        }

        // Append tapped #
        userEnteredPIN = userEnteredPIN + view.getTag().toString().substring(0, 1);
        pinBoxArray[userEnteredPIN.length() - 1].setBackgroundResource(R.drawable.rounded_view_dark_blue);

        // Perform appropriate action if PIN_LENGTH has been reached
        if (userEnteredPIN.length() == PIN_LENGTH) {

            // Throw error on '0000' to avoid server-side type issue
            if (userEnteredPIN.equals("0000")) {
                ToastCustom.makeText(PinEntryActivity.this, getString(R.string.zero_pin), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                clearPinBoxes();
                userEnteredPIN = "";
                userEnteredPINConfirm = null;
                return;
            }

            // Validate
            if (PrefsUtil.getInstance(PinEntryActivity.this).getValue(PrefsUtil.KEY_PIN_IDENTIFIER, "").length() >= 1) {
                titleView.setVisibility(View.INVISIBLE);
                validatePIN(userEnteredPIN);
            }

            else if(userEnteredPINConfirm == null)
            {
                //End of Create -  Change to Confirm
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        PinEntryActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                titleView.setText(R.string.confirm_pin);
                                clearPinBoxes();
                                userEnteredPINConfirm = userEnteredPIN;
                                userEnteredPIN = "";
                            }
                        });
                    }
                }, 200);

            }else if (userEnteredPINConfirm != null && userEnteredPINConfirm.equals(userEnteredPIN))
            {
                //End of Confirm - Pin is confirmed
                createPINThread(userEnteredPIN); // Pin is confirmed. Save to server.

            } else {

                //End of Confirm - Pin Mismatch
                ToastCustom.makeText(PinEntryActivity.this, getString(R.string.pin_mismatch_error), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                clearPinBoxes();
                userEnteredPIN = "";
                userEnteredPINConfirm = null;
                titleView.setText(R.string.create_pin);
            }
        }
    }

    public void cancelClicked(View view) {
        clearPinBoxes();
        userEnteredPIN = "";
    }

    private void clearPinBoxes(){
        if(userEnteredPIN.length() > 0)	{
            for(int i = 0; i < pinBoxArray.length; i++)	{
                pinBoxArray[i].setBackgroundResource(R.drawable.rounded_view_blue_white_border);//reset pin buttons blank
            }
        }
    }
}