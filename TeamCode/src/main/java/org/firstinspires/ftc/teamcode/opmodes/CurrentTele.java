package org.firstinspires.ftc.teamcode.opmodes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;

import org.firstinspires.ftc.teamcode.hardware.Robot;
import org.firstinspires.ftc.teamcode.hardware.events.LiftEvent;
import org.firstinspires.ftc.teamcode.hardware.events.PowershotEvent;
import org.firstinspires.ftc.teamcode.hardware.events.TurretEvent;
import org.firstinspires.ftc.teamcode.hardware.autoshoot.Tracker;
import org.firstinspires.ftc.teamcode.input.ControllerMap;
import org.firstinspires.ftc.teamcode.util.Persistent;
import org.firstinspires.ftc.teamcode.util.Scheduler;
import org.firstinspires.ftc.teamcode.util.Time;
import org.firstinspires.ftc.teamcode.util.event.EventBus;
import org.firstinspires.ftc.teamcode.util.event.EventBus.Subscriber;
import org.firstinspires.ftc.teamcode.util.event.EventFlow;
import org.firstinspires.ftc.teamcode.util.event.TimerEvent;
import org.firstinspires.ftc.teamcode.util.event.TriggerEvent;

@TeleOp(name="!!THE TeleOp!!")
public class CurrentTele extends LoggingOpMode {
    private Robot robot;
    private Tracker tracker;
    private ControllerMap controllerMap;
    
    private ControllerMap.AxisEntry   ax_drive_l;
    private ControllerMap.AxisEntry   ax_drive_r;
    private ControllerMap.AxisEntry   ax_intake;
    private ControllerMap.AxisEntry   ax_intake_out;
    private ControllerMap.AxisEntry   ax_turret;
    private ControllerMap.ButtonEntry btn_turret_reverse;
    private ControllerMap.ButtonEntry btn_shooter;
    private ControllerMap.ButtonEntry btn_pusher;
    private ControllerMap.ButtonEntry btn_wobble_up;
    private ControllerMap.ButtonEntry btn_wobble_down;
    private ControllerMap.ButtonEntry btn_wobble_open;
    private ControllerMap.ButtonEntry btn_wobble_close;
    private ControllerMap.ButtonEntry btn_slow;
    private ControllerMap.ButtonEntry btn_slow2;
    private ControllerMap.ButtonEntry btn_wobble_int;
    private ControllerMap.ButtonEntry btn_turret_home;
    private ControllerMap.ButtonEntry btn_shooter_preset;
    private ControllerMap.ButtonEntry btn_aim;
    private ControllerMap.ButtonEntry btn_powershot;
    
    private double driveSpeed;
    private double slowSpeed;
    private double lastUpdate;
    
    private boolean lift_up = false;
    private boolean shooter_on = false;
    private int slow = 0;
    
    private EventBus evBus;
    private Scheduler scheduler; // just in case
    private EventFlow liftFlow;
    private EventFlow powershotFlow;
    
    private int shooterPowerIdx;
    
    private static final int TRIGGER_LIFT_FLOW = 0;
    private static final int TRIGGER_POWERSHOT_FLOW = 0;
    
    private double[] speeds;
    private double[] powershot_angle;
    private  double[] powershot_powers;

    @Override
    public void init()
    {
        robot = new Robot(hardwareMap);
        // TODO load configuration for tracker
        tracker = new Tracker(robot.turret, robot.drivetrain, 135);
        evBus = new EventBus();
        scheduler = new Scheduler(evBus);
        
        liftFlow = new EventFlow(evBus);
        powershotFlow = new EventFlow(evBus);

        Scheduler.Timer liftTimer = scheduler.addPendingTrigger(0.2, "Lift Timer");

        JsonObject config = robot.config.getAsJsonObject("teleop");
        JsonArray powershotAngle = config.getAsJsonArray("powershot_angles");
        JsonArray powershotPowers = config.getAsJsonArray("powershot_powers");
        powershot_angle = new double[powershotAngle.size()];
        for (int i = 0; i < powershotAngle.size(); i++)
        {
            speeds[i] = powershotAngle.get(i).getAsDouble();
        }
        powershot_powers = new double[powershotPowers.size()];
        for (int i = 0; i < powershotPowers.size(); i++)
        {
            speeds[i] = powershotPowers.get(i).getAsDouble();
        }

        liftFlow.start(new Subscriber<>(TriggerEvent.class, (ev, bus, sub) -> {
                    robot.turret.home();
                }, "Home Turret", TRIGGER_LIFT_FLOW))
                .then(new Subscriber<>(TurretEvent.class, (ev, bus, sub) -> {
                    robot.lift.up();
                }, "Lift Up", TurretEvent.TURRET_MOVED))
                .then(new Subscriber<>(LiftEvent.class, (ev, bus, sub) -> {
                    liftTimer.reset();
                }, "Lift Wait", LiftEvent.LIFT_MOVED))
                .then(new Subscriber<>(TimerEvent.class, (ev, bus, sub) -> {
                    robot.lift.down();
                }, "Lift Down", liftTimer.eventChannel))
                .then(new Subscriber<>(LiftEvent.class, (ev, bus, sub) -> {},
                   "Lift Finished", LiftEvent.LIFT_MOVED)); // implicitly jump to beginning

        powershotFlow.start(new Subscriber<>(PowershotEvent.class, (ev, bus, sub) -> {
                    robot.turret.unpush();
                    robot.turret.shooter.setPower(powershot_powers[0]);
                    robot.turret.rotate(powershot_angle[0]);
                }, "Turn Powershot 1", PowershotEvent.TRIGGER_POWERSHOT))
                .then(new Subscriber<>(PowershotEvent.class, (ev, bus, sub) -> {
                    robot.turret.push();
                }, "Shoot Powershot 1", PowershotEvent.TURRET_AIMED))
                .then(new Subscriber<>(PowershotEvent.class, (ev, bus, sub) -> {
                    robot.turret.unpush();
                    robot.turret.shooter.setPower(powershot_powers[1]);
                    robot.turret.rotate(powershot_angle[1]);
                }, "Turn Powershot 2", PowershotEvent.RING_SHOT))
                .then(new Subscriber<>(PowershotEvent.class, (ev, bus, sub) -> {
                    robot.turret.push();
                }, "Shoot Powershot 2", PowershotEvent.TURRET_AIMED))
                .then(new Subscriber<>(PowershotEvent.class, (ev, bus, sub) -> {
                    robot.turret.unpush();
                    robot.turret.shooter.setPower(powershot_powers[2]);
                    robot.turret.rotate(powershot_angle[2]);
                }, "Turn Powershot 3", PowershotEvent.RING_SHOT))
                .then(new Subscriber<>(PowershotEvent.class, (ev, bus, sub) -> {
                    robot.turret.push();
                }, "Shoot Powershot 3", PowershotEvent.TURRET_AIMED));
        
        robot.lift.connectEventBus(evBus);
        robot.turret.connectEventBus(evBus);
        
        controllerMap = new ControllerMap(gamepad1, gamepad2);
        /*
         Hardware required:
         -- drivetrain (4 motors, tank, 2 axes)
         -- intake + ramp (2 motors, 1 button)
         -- turret (1 motor + 1 potentiometer/encoder, 1 axis + closed loop control)
         -- lift (2 servos, 1 button + toggle)
         -- shooter (1 motor + speed control, 1 button + toggle; telemetry for speed output)
         -- pusher (1 servo, 1 button)
         */
        controllerMap.setAxisMap  ("drive_l",     "gamepad1", "left_stick_y" );
        controllerMap.setAxisMap  ("drive_r",     "gamepad1", "right_stick_y");
        controllerMap.setAxisMap  ("intake",      "gamepad1", "right_trigger");
        controllerMap.setAxisMap  ("intake_out",  "gamepad1", "left_trigger");
        controllerMap.setAxisMap  ("turret",      "gamepad2", "left_stick_x" );
        controllerMap.setButtonMap("slow2",       "gamepad1", "right_bumper" );
        controllerMap.setButtonMap("turr_reverse","gamepad2", "left_trigger");
        controllerMap.setButtonMap("shooter",     "gamepad2", "y");
        controllerMap.setButtonMap("pusher",      "gamepad2", "x");
        controllerMap.setButtonMap("wobble_up",   "gamepad2", "dpad_up");
        controllerMap.setButtonMap("wobble_dn",   "gamepad2", "dpad_down");
        controllerMap.setButtonMap("wobble_o",    "gamepad2", "dpad_left");
        controllerMap.setButtonMap("wobble_c",    "gamepad2", "dpad_right");
        controllerMap.setButtonMap("slow",        "gamepad1", "left_bumper");
        controllerMap.setButtonMap("wobble_i",    "gamepad2", "left_bumper");
        controllerMap.setButtonMap("turr_home",   "gamepad2", "a");
        controllerMap.setButtonMap("shoot_pre",   "gamepad2", "right_bumper");
        controllerMap.setButtonMap("aim",         "gamepad2", "b");
        controllerMap.setButtonMap("powershot",   "gamepad1", "a");
        
        ax_drive_l      = controllerMap.axes.get("drive_l");
        ax_drive_r      = controllerMap.axes.get("drive_r");
        ax_intake       = controllerMap.axes.get("intake");
        ax_intake_out   = controllerMap.axes.get("intake_out");
        ax_turret       = controllerMap.axes.get("turret");
        btn_shooter     = controllerMap.buttons.get("shooter");
        btn_pusher      = controllerMap.buttons.get("pusher");
        btn_wobble_up   = controllerMap.buttons.get("wobble_up");
        btn_wobble_down = controllerMap.buttons.get("wobble_dn");
        btn_wobble_open = controllerMap.buttons.get("wobble_o");
        btn_wobble_close= controllerMap.buttons.get("wobble_c");
        btn_slow        = controllerMap.buttons.get("slow");
        btn_slow2       = controllerMap.buttons.get("slow2");
        btn_wobble_int  = controllerMap.buttons.get("wobble_i");
        btn_turret_home = controllerMap.buttons.get("turr_home");
        btn_shooter_preset = controllerMap.buttons.get("shoot_pre");
        btn_turret_reverse = controllerMap.buttons.get("turr_reverse");
        btn_aim = controllerMap.buttons.get("aim");

        JsonArray driveSpeeds = config.getAsJsonArray("drive_speeds");
        speeds = new double[driveSpeeds.size()];
        for (int i = 0; i < driveSpeeds.size(); i++)
        {
            speeds[i] = driveSpeeds.get(i).getAsDouble();
        }
        robot.wobble.up();
        
        robot.imu.initialize(evBus, scheduler);

        robot.turret.startZeroFind();

        if (Persistent.get("turret_zero_found") == null)
            robot.turret.startZeroFind();

    }

    @Override
    public void init_loop()
    {
        robot.turret.updateInit(telemetry);
    }
    
    @Override
    public void start()
    {
        lastUpdate = Time.now();
        Persistent.clear();
    }
    
    @Override
    public void loop()
    {
        double dt = Time.since(lastUpdate);
        lastUpdate = Time.now();
        double speed = speeds[slow];
        // TODO unswap control axes
        robot.drivetrain.telemove(ax_drive_r.get() * speed,
                                 ax_drive_l.get() * speed);
        

        robot.intake.run(ax_intake.get() - ax_intake_out.get());

        //if (btn_aim.get()){
        //    tracker.updateVars();
        //}
        double turret_adj = -ax_turret.get() * 0.003;
        robot.turret.rotate(robot.turret.getTarget() + turret_adj);

        if (btn_shooter.edge() > 0)
        {
            shooter_on = !shooter_on;
            if (shooter_on) robot.turret.shooter.start();
            else            robot.turret.shooter.stop();
        }
        
        if (btn_shooter_preset.edge() > 0)
        {
            shooterPowerIdx += 1;
            robot.turret.shooter.setPreset(shooterPowerIdx);
            robot.controlHub.setLEDColor(robot.turret.shooter.getPresetColor());
        }
        
        if (btn_slow.edge() > 0)
        {
            if (slow == 0) slow = 1;
            else slow = 0;
        }
        if (btn_slow2.edge() > 0)
        {
            if (slow == 0) slow = 2;
            else slow = 0;
        }
        
        
        if (btn_pusher.get()) robot.turret.push();
        else                  robot.turret.unpush();
        
        if (btn_turret_home.edge() > 0) robot.turret.home();
        if (btn_turret_reverse.edge() > 0) robot.turret.rotate(robot.turret.getTurretShootPos());
        
        if (btn_wobble_up.get()) robot.wobble.up();
        if (btn_wobble_down.get()) robot.wobble.down();
        if (btn_wobble_open.get()) robot.wobble.open();
        if (btn_wobble_close.get()) robot.wobble.close();
        if (btn_wobble_int.get()) robot.wobble.middle();

        if (btn_powershot.edge() > 0){
            evBus.pushEvent(new PowershotEvent(PowershotEvent.TRIGGER_POWERSHOT));
        }
        
        robot.lift.update(telemetry);
        robot.turret.update(telemetry);
        robot.drivetrain.getOdometry().updateDeltas();
        telemetry.addData("Shooter Velocity", "%.3f",
                ((DcMotorEx)robot.turret.shooter.motor).getVelocity());
        telemetry.addData("Shooter speed preset", robot.turret.shooter.getCurrPreset());
        telemetry.addData("Turret target heading", "%.3f", tracker.getTargetHeading());
        telemetry.addData("Odometry position", "%.3f,%.3f", robot.drivetrain.getOdometry().x, robot.drivetrain.getOdometry().y);
        telemetry.addData("Turret Current Position", robot.turret.turretFb.getCurrentPosition());
        scheduler.loop();
        evBus.update();
        // telemetry.addData("Turret power", "%.3f", robot.turret.turret.getPower());
    }
}
