package org.firstinspires.ftc.teamcode.glados.components;

import androidx.annotation.NonNull;
import com.qualcomm.robotcore.hardware.DcMotor;
import org.firstinspires.ftc.teamcode.common.BunyipsOpMode;
import org.firstinspires.ftc.teamcode.common.OldMecanumDrive;

public class GLaDOSPOVDriveCore extends OldMecanumDrive {
    public GLaDOSPOVDriveCore(@NonNull BunyipsOpMode opMode, DcMotor frontLeft, DcMotor backLeft, DcMotor frontRight, DcMotor backRight) {
        super(opMode, frontLeft, backLeft, frontRight, backRight);
    }
}