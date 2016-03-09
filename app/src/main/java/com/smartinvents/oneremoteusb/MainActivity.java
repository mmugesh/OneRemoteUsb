package com.smartinvents.oneremoteusb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TableRow.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class MainActivity extends Activity {

    public static final int ID_TEXT_VIEW_BUTTON_ARRAY = 1;

    // System Related Variables

    private static OutputStream logfile = null;
    private OneRemote oneRemote;
    boolean bOneRemoteInit = false;
    private HashMap< String, Remote> remotes = null;
    SharedPreferences mPrefs;

    // UI Related Variables
    ScrollView scrollViewButtonArray;
    LinearLayout llVerticalMain;
    LinearLayout.LayoutParams lllpMain;

    TextView textviewBrowse;
    Button buttonBrowse;

    ArrayAdapter<String> arrayadapterRemoteName;
    Spinner spinnerRemoteName;

    TableLayout tablelayoutButtonArray;
    TextView textviewButtonArray;
    private int intBtnArrayColCnt = 2;

    private Vibrator m_vibBtnPressed;
    private String m_strConfFilename = null;
    private String m_strConfFileDirectory= null;
    private static String m_strAppDirectory= null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        File appFolder;
        if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
            // External Memory Of Phone
            appFolder = new File(Environment.getExternalStorageDirectory() + File.separator + getString(R.string.app_name));
        }
        else{
            // Internal Memory Of Phone
            appFolder = new File("/data/data/" + getPackageName() + File.separator + getString(R.string.app_name));
        }

        // Save Application Folder
      /*  try {
            m_strAppDirectory = appFolder.getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
*/
        // Create Application Folder
        appFolder.mkdirs();
        firstRunPreferences();

        llVerticalMain = new LinearLayout(this);
        llVerticalMain.setOrientation(LinearLayout.VERTICAL);

        scrollViewButtonArray = new ScrollView(this);

        setContentView(llVerticalMain);

        lllpMain = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);

        lllpMain.weight = 1f;

        LinearLayout llHorizontalInitClear = new LinearLayout(this);
        llHorizontalInitClear.setOrientation(LinearLayout.HORIZONTAL);



        llVerticalMain.addView(llHorizontalInitClear);

        // Draw A Line
        View viewLine = new View(this);
        viewLine.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 3));
        viewLine.setBackgroundColor(Color.rgb(51, 181, 229));
        llVerticalMain.addView(viewLine);

        TextView textviewBrowseInst = new TextView(this);
        textviewBrowseInst.setText(R.string.str_tvw_browse_inst);
        llVerticalMain.addView(textviewBrowseInst);

        LinearLayout llHorizontalBrowse = new LinearLayout(this);
        llHorizontalBrowse.setOrientation(LinearLayout.HORIZONTAL);

        textviewBrowse = new TextView(this);
        textviewBrowse.setGravity(Gravity.CENTER_VERTICAL);
        textviewBrowse.setTextColor(0xFFFFFFFF);
        textviewBrowse.setLayoutParams(lllpMain);
        llHorizontalBrowse.addView(textviewBrowse);

        buttonBrowse = new Button(this);
        buttonBrowse.setText(R.string.str_btn_browse);
        buttonBrowse.setOnClickListener(clickButtonBrowse);
        LinearLayout.LayoutParams lllpBrowse = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        llHorizontalBrowse.addView(buttonBrowse, lllpBrowse);

        llVerticalMain.addView(llHorizontalBrowse);

        LinearLayout llHorizontalSelRemote = new LinearLayout(this);
        llHorizontalSelRemote.setOrientation(LinearLayout.HORIZONTAL);

        TextView textviewRemoteName = new TextView(this);
        textviewRemoteName.setText(R.string.str_tvw_sel_rmt);
        LinearLayout.LayoutParams layoutParamsRemoteName = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llHorizontalSelRemote.addView(textviewRemoteName, layoutParamsRemoteName);


        // Initialize Adapter For Remote Name Spinner
        arrayadapterRemoteName = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item);
        arrayadapterRemoteName.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Initialize Remote Name Spinner
        spinnerRemoteName = new Spinner(this);
        spinnerRemoteName.setPrompt(getString(R.string.str_tvw_sel_rmt));
        spinnerRemoteName.setAdapter(arrayadapterRemoteName);
        spinnerRemoteName.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                // Re-draw Direct Button Name
                String strRemoteName = spinnerRemoteName.getSelectedItem().toString();

                drawUiButtonArray(strRemoteName);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {

            }
        });

        LinearLayout.LayoutParams layoutParamsSelRemote = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llHorizontalSelRemote.addView(spinnerRemoteName, layoutParamsSelRemote);
        llVerticalMain.addView(llHorizontalSelRemote);

        tablelayoutButtonArray = new TableLayout(this);
        tablelayoutButtonArray.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);
        tablelayoutButtonArray.setGravity(Gravity.CENTER);

        textviewButtonArray = new TextView(this);
        textviewButtonArray.setId(ID_TEXT_VIEW_BUTTON_ARRAY);
        textviewButtonArray.setText(R.string.str_tvw_press_btn);

        m_vibBtnPressed = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
        this.remotes = new HashMap<String, Remote>();



        // Retrieve Previous Button Column Count Setting
        intBtnArrayColCnt = getButtonColCnt();

        // Retrieve Last Lirc Config File If Any
        m_strConfFilename = getLastLircConfFile();
        if(m_strConfFilename != null){
            parseLircFile(m_strConfFilename);
        }
        else{
            drawUiAbout();
        }

        oneRemote = new OneRemote(this);


    }

    @Override
    protected void onDestroy() {
        oneRemote.Close();

        try {
            logfile.flush();
            logfile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logfile = null;

        super.onDestroy();
    }

    OnClickListener clickButtonBrowse = new OnClickListener() {
        @Override
        public void onClick(View view) {
            m_vibBtnPressed.vibrate(50);
            // Pop Up Dialog Browse File
            drawUiBrowseFileDialog();
        }
    };



    OnClickListener clickButtonRemoteKey = new OnClickListener() {
        @Override
        public void onClick(View view) {
            m_vibBtnPressed.vibrate(50);
            if (!bOneRemoteInit) {
                Toast.makeText(getApplicationContext(), R.string.str_fail_reinit, Toast.LENGTH_SHORT).show();

                return;
            }

            Button btnPressed = (Button)view;
            String strRemoteName = spinnerRemoteName.getSelectedItem().toString();
            String strButttonName = btnPressed.getText().toString();

            sendButton(strRemoteName, strButttonName);
        }
    };

    /**
     * Setting Up Preferences Storage
     */
    public void firstRunPreferences() {
        Context mContext = this.getApplicationContext();
        mPrefs = mContext.getSharedPreferences("usbOneRemotePrefs", MODE_PRIVATE);
    }

    /**
     * Store The First Run
     */
    public void setRunned() {
        SharedPreferences.Editor edit = mPrefs.edit();
        edit.putBoolean("firstRun", false);
        edit.apply();
    }

    /**
     * Get If This Is The First Run
     * @return True, If This Is The First Run
     */
    public boolean getFirstRun() {
        return mPrefs.getBoolean("firstRun", true);
    }

    public String getLastLircConfFile() {
        return mPrefs.getString("LastLircConfFile", null);
    }

    public void setLastLircConfFile(String strFilename) {
        SharedPreferences.Editor edit = mPrefs.edit();
        edit.putString("LastLircConfFile", strFilename);
        edit.apply();

    }

    /**
     * Get Button Column Count Setting
     * @return int
     */
    public int getButtonColCnt() {
        return mPrefs.getInt("buttonColCnt", 2);
    }

    /**
     * Store Button Column Count Setting
     */
    public void setButtonColCnt(int iBtnColCnt) {
        SharedPreferences.Editor edit = mPrefs.edit();
        edit.putInt("buttonColCnt", iBtnColCnt);
        edit.apply();

    }


    public void initOneRemote() {
        try {
            if (oneRemote.init()){
                bOneRemoteInit = true;
                Toast.makeText(getApplicationContext(), R.string.str_init_ok, Toast.LENGTH_SHORT).show();

            }
            else{
                Toast.makeText(getApplicationContext(), R.string.str_init_fail, Toast.LENGTH_SHORT).show();

            }
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();

        }
    }

    public void parseLircFile(String strFilename) {
        // Update UI
        textviewBrowse.setText(strFilename);

        // Parse Lirc Conf File
        discoverRemotes(strFilename);
    }

    public void discoverRemotes(String strFilename) {
        File file = new File(strFilename);

        // Save Browsed Directory
        m_strConfFileDirectory = file.getParent();
        setLastLircConfFile(strFilename);

        try {
            InputStreamReader isr = new InputStreamReader(new FileInputStream(file));
            ConfParser parser = new ConfParser(isr);

            this.remotes.clear();
            for (Remote remote : parser.Parse()) {
                String strRemoteName = remote.getName();
                this.remotes.put(strRemoteName, remote);

            }

            // Re-Populate Spinner Remote Name
            updateRemoteNameSpinner();

            // Re-Draw Direct Button Name
            String strRemoteName = spinnerRemoteName.getSelectedItem().toString();
            drawUiButtonArray(strRemoteName);

        } catch (UnsupportedEncodingException e) {
            Toast.makeText(getApplicationContext(), "UnsupportedEncodingException while parsing lirc file", Toast.LENGTH_SHORT).show();
            e.printStackTrace();

        } catch (FileNotFoundException e) {
            Toast.makeText(getApplicationContext(), "FileNotFoundException while parsing lirc file", Toast.LENGTH_SHORT).show();
            e.printStackTrace();

        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Exception while parsing lirc file", Toast.LENGTH_SHORT).show();
            e.printStackTrace();

        }
    }

    public void sendButton(String strRemoteName, String strButtonName) {
        String strSendCmd = "";
        String strButtonCode;
        Remote remote;

        remote = this.remotes.get(strRemoteName);
        strButtonCode = remote.getButtonsCode(strButtonName);

        Toast.makeText(getApplicationContext(), "Send [" + strButtonName + ", " + strButtonCode + "]", Toast.LENGTH_SHORT).show();

        ArrayList<Long> arraylistRawCode = remote.playButton(strButtonName);
        for (long lTmp : arraylistRawCode){
            lTmp = (long) (lTmp / 21.33);
            String strHexRawCode = String.format("%04X", (0xFFFF & lTmp));
            strSendCmd += strHexRawCode.substring(0,2) + " " + strHexRawCode.substring(2,4) + " ";
        }

        // Must Necessarily Conclude With 'ff ff'!
        strSendCmd += "FF FF";
        oneRemote.sendCommandAsync(strSendCmd);
    }

    // Re-Populate Spinner Remote Name
    public void updateRemoteNameSpinner() {
        arrayadapterRemoteName.clear();
        for (String strRemoteName : this.remotes.keySet()) {
            arrayadapterRemoteName.add(strRemoteName);
        }

    }

    public void drawUiBrowseFileDialog() {
        // Create FileOpenDialog And Register A Callback
        SimpleFileDialog FileOpenDialog =  new SimpleFileDialog(MainActivity.this, "FileOpen", new SimpleFileDialog.SimpleFileDialogListener() {
            @Override
            // The Code In This Function Will Be Executed When The Dialog Ok Button Is Pushed
            public void onChosenDir(String chosenDir) {
                m_strConfFilename = chosenDir;
                parseLircFile(m_strConfFilename);
            }
        });

        // Default Filename Using The Public Variable "Default_File_Name"
        FileOpenDialog.Default_File_Name = "";
        if (m_strConfFileDirectory == null){

            FileOpenDialog.chooseFile_or_Dir();
        }
        else{

            FileOpenDialog.chooseFile_or_Dir(m_strConfFileDirectory);
        }
    }


    public void drawUiDialogChooseBtnColCnt() {
        AlertDialog.Builder alertdialogbuilderChooseBtnColCnt = new AlertDialog.Builder(this);
        alertdialogbuilderChooseBtnColCnt.setTitle(R.string.str_menu_button_col_cnt_title);
        CharSequence[] strBtnColCnt = {"1", "2", "3", "4", "5", "6"};
        alertdialogbuilderChooseBtnColCnt.setItems(strBtnColCnt, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                intBtnArrayColCnt = which + 1;
                setButtonColCnt(intBtnArrayColCnt);

                // Re-Populate Spinner Button Name
                String strRemoteName = spinnerRemoteName.getSelectedItem().toString();

                // Re-Draw Direct Button Name
                drawUiButtonArray(strRemoteName);
            }
        });
        alertdialogbuilderChooseBtnColCnt.show();
    }

    public void drawUiButtonArray(String strRemoteName) {
        Remote remote = this.remotes.get(strRemoteName);
        ArrayList<String> arraylistButtonName = remote.getButtonsNames();
        Iterator<String> iteratorButtonName = arraylistButtonName.iterator();


        llVerticalMain.removeView(scrollViewButtonArray);


        tablelayoutButtonArray.removeAllViews();
        scrollViewButtonArray.removeAllViews();


        llVerticalMain.removeView(textviewButtonArray);
        llVerticalMain.addView(textviewButtonArray);


        String strButtonText;
        // Loop For Vertically Number Of Row Of Button
        while (iteratorButtonName.hasNext()){
            TableRow tablerowButtonArray = new TableRow(this);
            LayoutParams layoutparamsButtonArray = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
//			layoutparamsButtonArray.weight = 1f;
            tablerowButtonArray.setLayoutParams(layoutparamsButtonArray);
            // Loop For Horizontally Number Of Button
            for (int j = 0; j < intBtnArrayColCnt; j++) {
                Button btn = new Button(this);
                if(iteratorButtonName.hasNext()){
                    strButtonText = iteratorButtonName.next();
                    btn.setText(strButtonText);
                    btn.setOnClickListener(clickButtonRemoteKey);
                    tablerowButtonArray.addView(btn);
                }
            }

            TableRow.LayoutParams tablerowlayoutparamsButton = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            tablerowlayoutparamsButton.gravity = Gravity.FILL_HORIZONTAL;
            tablelayoutButtonArray.addView(tablerowButtonArray, tablerowlayoutparamsButton);
        }

        LinearLayout.LayoutParams lllpButtonArray = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollViewButtonArray.addView(tablelayoutButtonArray, lllpButtonArray);
        llVerticalMain.addView(scrollViewButtonArray, lllpButtonArray);
    }

    public String drawUiAbout(){
        String strVersionName = "";
        try {
            strVersionName = " Version " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        AlertDialog.Builder alertdialogbuilderAbout = new AlertDialog.Builder(this);
        String strTitle = getString(R.string.app_name) + strVersionName;
        alertdialogbuilderAbout.setTitle(strTitle)
                .setIcon(R.drawable.ic_launcher)
                .setMessage(R.string.str_about_info)
                .setCancelable(true)
                .setNegativeButton(R.string.str_about_dismiss, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });

        AlertDialog alertdialogAboutAlert = alertdialogbuilderAbout.create();
        alertdialogAboutAlert.show();
        // Make The Textview Clickable. Must Be Called After show()
        ((TextView)alertdialogAboutAlert.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());

        return null;
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, R.string.str_menu_reset).setIcon(android.R.drawable.ic_lock_power_off);
        menu.add(0, 1, 0, R.string.str_menu_browse_lirc).setIcon(android.R.drawable.ic_menu_add);
        menu.add(0, 2, 0, R.string.str_menu_button_col_cnt).setIcon(android.R.drawable.ic_menu_add);
        menu.add(0, 3, 0, R.string.str_menu_about).setIcon(android.R.drawable.ic_menu_help);

        return true;
    }


    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                initOneRemote();
                break;
            case 1:
                drawUiBrowseFileDialog();
                break;
            case 2:
                drawUiDialogChooseBtnColCnt();
                break;
            case 3:
                drawUiAbout();
                break;
        }
        return false;
    }
}

