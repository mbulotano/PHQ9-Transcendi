package morningsignout.phq9transcendi.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import morningsignout.phq9transcendi.R;

/**
 * Created by pokeforce on 5/24/16.
 */
public class ResultsActivity extends AppCompatActivity implements View.OnClickListener {
    static public final String SCORE = "Score",
        RED_FLAG = "Red Flag";

    // For every screen
    Button finishUpButton;

    // First screen
    LinearLayout scoreContainer;
    TextView allDoneText;
    TextView scoreText;
    TextView redFlagText;
    ImageView scoreFan;

    // Second screen
    BlinkScrollView blinkScrollView;
    TextView detailsText;

    int totalScore;
    boolean redFlag;
    int screenNumber = 0;
    AlertDialog.Builder dialogBuilder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Utils.onActivityCreateSetTheme(this, Utils.GetTheme(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        Intent args = getIntent();
        if (args == null)
            throw new NullPointerException("Results activity started without arguments");

        // Results of quiz
        totalScore = args.getIntExtra(SCORE, 0);
        redFlag = args.getBooleanExtra(RED_FLAG, false);

        // Views for results activity
        finishUpButton = (Button) findViewById(R.id.button_finish_up);      // Moves to next screen
        allDoneText = (TextView) findViewById(R.id.textView_all_done);      // First screen, shows main text
        scoreText = (TextView) findViewById(R.id.textView_score);           // Score #
        redFlagText = (TextView) findViewById(R.id.textView_red_flag);
        scoreFan = (ImageView) findViewById(R.id.imageView_score_fan);
        detailsText = (TextView) findViewById(R.id.textView_details);       // Second screen
        blinkScrollView = (BlinkScrollView) findViewById(R.id.container_detail);

        // Blink when scrollable (once)
        detailsText.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (v.getHeight() != 0 && blinkScrollView.canScrollVertically()) {
                    blinkScrollView.blinkScrollBar();
                    v.removeOnLayoutChangeListener(this);   // Remove listener after blink
                }
            }
        });

        // Configuration-specific variables
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            scoreContainer = (LinearLayout) findViewById(R.id.container_score);

            String allDone = getResources().getString(R.string.all_done);
            allDone = String.format(allDone, totalScore);
            allDoneText.setText(allDone);

            if (!redFlag)
                redFlagText.setVisibility(View.INVISIBLE);
        } else {
            scoreContainer = null;

            String allDone = getResources().getString(R.string.all_done_land);
            allDone = String.format(allDone, totalScore);
            allDoneText.setText(allDone);

            if (!redFlag)
                redFlagText.setVisibility(View.GONE);
        }

        scoreText.setText(String.valueOf(totalScore));
        finishUpButton.setOnClickListener(this);
        dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(R.string.app_name)
                .setMessage(R.string.dialog_quit_results)
                .setPositiveButton(R.string.dialog_return_home, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent backToMenu = new Intent(ResultsActivity.this, IndexActivity.class);
                        backToMenu.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(backToMenu);
                        finish();
                    }
                }).setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });

        // Restore state
        if (savedInstanceState != null) {
            screenNumber = savedInstanceState.getInt("Screen Number", 0);
            if (screenNumber > 0) {
                screenNumber--;
                onClick(null);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("Screen Number", screenNumber);
    }

    @Override
    public void onBackPressed() {
        dialogBuilder.create().show();
    }

    @Override
    public void onClick(View v) {
        switch (screenNumber) {
            case 0:
                // Remove unneeded views
                if (scoreContainer != null)
                    scoreContainer.setVisibility(View.GONE);
                else {
                    allDoneText.setVisibility(View.GONE);
                    scoreText.setVisibility(View.GONE);
                    redFlagText.setVisibility(View.GONE);
                    scoreFan.setVisibility(View.GONE);
                }

                // Set new text & blink to indicate scrolling
                detailsText.setText(getResult());
                finishUpButton.setText(getResources().getText(R.string.finish_up));

                screenNumber = 1;
                break;
            case 1:
                // Remove unneeded views (in case of config changes)
                if (scoreContainer != null)
                    scoreContainer.setVisibility(View.GONE);
                else {
                    allDoneText.setVisibility(View.GONE);
                    scoreText.setVisibility(View.GONE);
                    redFlagText.setVisibility(View.GONE);
                    scoreFan.setVisibility(View.GONE);
                }

                blinkScrollView.setScrollY(0);
                detailsText.setText(getResources().getText(R.string.wrap_up_text));
                finishUpButton.setText(getResources().getText(R.string.take_me_home));
                screenNumber = 2;
                break;
            case 2:
                Intent backHome = new Intent(this, IndexActivity.class);
                startActivity(backHome);
                finish();
                break;
            default:
                Log.e("ResultsActivity", "Should not happen");
                break;
        }
    }

    String getResult() {
        String[] scoreEval = getResources().getStringArray(R.array.scoreEval);
        String result = getResources().getString(R.string.scoreResult);

        if(totalScore == 0) {
            result = String.format(result, totalScore, scoreEval[0]);
        } else if(totalScore >= 1 && totalScore < 5) {
            result = String.format(result, totalScore, scoreEval[1]);
        } else if(totalScore >= 5 && totalScore < 10) {
            result = String.format(result, totalScore, scoreEval[2]);
        } else if(totalScore >= 10 && totalScore < 15) {
            result = String.format(result, totalScore, scoreEval[3]);
        } else if(totalScore >= 15 && totalScore < 20) {
            result = String.format(result, totalScore, scoreEval[4]);
        } else if(totalScore >= 20) {
            result = String.format(result, totalScore, scoreEval[5]);
        }

        String[] scoreDetails = getResources().getStringArray(R.array.scoreDetails);
        result += "\n\n";

        if(redFlag) {
            result += scoreDetails[2];
        } else if (totalScore == 0) {
            result += scoreDetails[0];
        } else {
            result += scoreDetails[1];
        }

        return result;
    }
}