package mitko.htpccontrol;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        clientHandler_ = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.obj instanceof ArrayList) {
                    TextView text = (TextView) findViewById(R.id.status);
                    ArrayList<ArrayList<String>> command = (ArrayList<ArrayList<String>>) msg.obj;
                    for (ArrayList<String> line : command) {
                        boolean first = true;
                        for (String word : line) {
                            if (!first)
                                text.append(" | ");
                            text.append(word);
                            first = false;
                        }
                        text.append("\n");
                    }
                }
            }
        };

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        client_ = new TcpClient(preferences.getString("server_ip_address", ""),
                Integer.parseInt(preferences.getString("server_port", "0")), clientHandler_);
        client_.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    public final static String EXTRA_MESSAGE = "com.example.myfirstapp.MESSAGE";

    /** Called when the user clicks the Send button */
    public void sendMessage(View view) {
//        Intent intent = new Intent(this, DisplayMessageActivity.class);
        EditText editText =  (EditText) findViewById(R.id.edit_message);
        String message = editText.getText().toString();
//        intent.putExtra(EXTRA_MESSAGE, message);
//        startActivity(intent);
        client_.send(message.split(" "));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.open_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private TcpClient client_;
    private Handler clientHandler_;
}
