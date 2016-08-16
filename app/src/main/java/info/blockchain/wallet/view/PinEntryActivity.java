package info.blockchain.wallet.view;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import info.blockchain.wallet.access.AccessState;
import info.blockchain.wallet.callbacks.DialogButtonCallback;
import info.blockchain.wallet.connectivity.ConnectivityStatus;
import info.blockchain.wallet.util.CharSequenceX;
import info.blockchain.wallet.util.ViewUtils;
import info.blockchain.wallet.view.helpers.ToastCustom;
import info.blockchain.wallet.viewModel.PinEntryViewModel;

import piuk.blockchain.android.BaseAuthActivity;
import piuk.blockchain.android.R;
import piuk.blockchain.android.databinding.ActivityPinEntryBinding;

public class PinEntryActivity extends BaseAuthActivity implements PinEntryViewModel.DataListener {

    private static final int COOL_DOWN_MILLIS = 2 * 1000;
    private static final int PIN_LENGTH = 4;

    private TextView[] mPinBoxArray = null;
    private ProgressDialog mProgressDialog = null;
    private ActivityPinEntryBinding binding;
    private PinEntryViewModel mViewModel;

    private long mBackPressed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_pin_entry);
        mViewModel = new PinEntryViewModel(this);

        // Set title state
        if (mViewModel.isCreatingNewPin()) {
            binding.titleBox.setText(R.string.create_pin);
        } else {
            binding.titleBox.setText(R.string.pin_entry);
        }

        mPinBoxArray = new TextView[PIN_LENGTH];
        mPinBoxArray[0] = binding.pinBox0;
        mPinBoxArray[1] = binding.pinBox1;
        mPinBoxArray[2] = binding.pinBox2;
        mPinBoxArray[3] = binding.pinBox3;

        showConnectionDialogIfNeeded();

        mViewModel.onViewReady();
    }

    private void showConnectionDialogIfNeeded() {
        if (!ConnectivityStatus.hasConnectivity(this)) {
            new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                    .setMessage(getString(R.string.check_connectivity_exit))
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_continue, (dialog, id) -> restartPage())
                    .create()
                    .show();
        }
    }

    @Override
    public void showMaxAttemptsDialog() {
        new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setTitle(R.string.app_name)
                .setMessage(R.string.password_or_wipe)
                .setCancelable(false)
                .setPositiveButton(R.string.use_password, (dialog, whichButton) -> showValidationDialog())
                .setNegativeButton(R.string.wipe_wallet, (dialog, whichButton) -> mViewModel.resetApp())
                .show();
    }

    @Override
    public void onBackPressed() {
        if (mViewModel.allowExit()) {
            if (mBackPressed + COOL_DOWN_MILLIS > System.currentTimeMillis()) {
                AccessState.getInstance().logout(this);
                return;
            } else {
                ToastCustom.makeText(this, getString(R.string.exit_confirm), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_GENERAL);
            }

            mBackPressed = System.currentTimeMillis();
        }
    }

    @Override
    public void showWalletVersionNotSupportedDialog(int walletVersion) {
        new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setTitle(R.string.warning)
                .setMessage(String.format(getString(R.string.unsupported_encryption_version), walletVersion))
                .setCancelable(false)
                .setPositiveButton(R.string.exit, (dialog, whichButton) -> AccessState.getInstance().logout(this))
                .setNegativeButton(R.string.logout, (dialog, which) -> {
                    mViewModel.getAppUtil().clearCredentialsAndRestart();
                    mViewModel.getAppUtil().restartApp();
                })
                .show();
    }

    @Override
    public void goToUpgradeWalletActivity() {
        Intent intent = new Intent(this, UpgradeWalletActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void padClicked(View view) {
        mViewModel.padClicked(view);
    }

    @Override
    public void setTitleString(@StringRes int title) {
        binding.titleBox.setText(title);
    }

    @Override
    public void setTitleVisibility(@ViewUtils.Visibility int visibility) {
        binding.titleBox.setVisibility(visibility);
    }

    public void deleteClicked(View view) {
        mViewModel.onDeleteClicked();
    }

    @Override
    public TextView[] getPinBoxArray() {
        return mPinBoxArray;
    }

    @Override
    public void restartPage() {
        Intent intent = new Intent(this, PinEntryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void showCommonPinWarning(DialogButtonCallback callback) {
        new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setTitle(R.string.common_pin_dialog_title)
                .setMessage(R.string.common_pin_dialog_message)
                .setPositiveButton(R.string.common_pin_dialog_try_again, (dialogInterface, i) -> callback.onPositiveClicked())
                .setNegativeButton(R.string.common_pin_dialog_continue, (dialogInterface, i) -> callback.onNegativeClicked())
                .setCancelable(false)
                .create()
                .show();
    }

    @Override
    public void showValidationDialog() {
        final AppCompatEditText password = new AppCompatEditText(this);
        password.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setTitle(R.string.app_name)
                .setMessage(this.getString(R.string.password_entry))
                .setView(ViewUtils.getAlertDialogTextViewLayout(this, password))
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (dialog, whichButton) -> mViewModel.getAppUtil().restartApp())
                .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
                    final String pw = password.getText().toString();

                    if (pw.length() > 0) {
                        mViewModel.validatePassword(new CharSequenceX(pw));
                    } else {
                        mViewModel.incrementFailureCount();
                    }

                }).show();
    }

    @Override
    public void showToast(@StringRes int message, @ToastCustom.ToastType String toastType) {
        ToastCustom.makeText(this, getString(message), ToastCustom.LENGTH_SHORT, toastType);
    }

    @Override
    public void showProgressDialog(@StringRes int messageId, @Nullable String suffix) {
        dismissProgressDialog();
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setTitle(R.string.app_name);
        if (suffix != null) {
            mProgressDialog.setMessage(getString(messageId) + suffix);
        } else {
            mProgressDialog.setMessage(getString(messageId));
        }

        if (!isFinishing()) mProgressDialog.show();
    }

    @Override
    public void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mViewModel.clearPinBoxes();
    }

    @Override
    public Intent getPageIntent() {
        return getIntent();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Test for screen overlays before user enters PIN
        // consume event
        return mViewModel.getAppUtil().detectObscuredWindow(event) || super.dispatchTouchEvent(event);
    }

    @Override
    protected void startLogoutTimer() {
        // No-op
    }

    @Override
    protected void onDestroy() {
        mViewModel.destroy();
        super.onDestroy();
    }
}