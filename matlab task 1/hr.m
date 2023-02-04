%clear stuff
clf
clear
clc

%Load the ECG data
EKG = load('sensor_data.mat');

EKG_1 = EKG.val(1,:);
EKG_2 = EKG.val(2,:);

%time step vector
ts = (0:10/length(EKG_1):10-10/length(EKG_1));

%plot data
plot(ts, EKG_1)
hold on;

title('Hear rate data')
xlabel('Time (in seconds)')
ylabel('ECG amplitude')

%[PkAmp, PkTime] = findpeaks(Displacement)
[PkAmp, PkTime] = findpeaks(EKG_1, 'MinPeakHeight', 1100);
Actual_Time = PkTime/length(EKG_1);