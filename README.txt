In order to run the project should be just enough to perform following steps:
	1. Extract archive to destination directory and open it in IntelliJ
	2. Edit configurations > Set src/App to be built and run
	3. Go to File > Project structure > Global libraries and select directory with pre-extracted LWJGL binaries (LWJGL 3.2.3 build 13)
	4. Right click `res` and `shaders` subdirectories and mark those as Resources root
	5. Run the application

Controls:
	WASD 	- camera horizontal movement
	QE		- camera vertical movement (Up/Down)
	Shift	- press to accelerate movement
	Mouse left clik - change camera direction

Basic application description:
	Demo scene contains a plane and a set of navigation vectors, that define plane trajectory.
	Vectors are contained within a single List and Plane moves between them dynamically in consecutive order.
	Plane trajectory is interpolated with Bezier curves and its orientation is interpolated with Quaternions.
	