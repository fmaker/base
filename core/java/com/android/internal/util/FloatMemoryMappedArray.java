package com.android.internal.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;

public class FloatMemoryMappedArray{
	private final int INT_BYTES = 4;

	int arraySize, xDim, yDim, zDim;
	FileChannel fileChannel;
	FloatBuffer fBuffer;

	public FloatMemoryMappedArray(int x, int y, int z, File tmp){
		arraySize = x*y*z;
		xDim = x;
		yDim = y;
		zDim = z;

		try {
			tmp.delete();
			fileChannel = new RandomAccessFile(tmp,"rw").getChannel();
			ByteBuffer buffer = fileChannel.map(MapMode.READ_WRITE,0,arraySize*INT_BYTES);
			fBuffer = buffer.asFloatBuffer();

		} catch (FileNotFoundException e) {
			e.printStackTrace();

		} catch (IOException e) {
			e.printStackTrace();

		}
	}
	
	public void fill(float e){
		if (fBuffer.hasArray()){
			int ao = fBuffer.arrayOffset();
			int pos = fBuffer.position();
			int rem = fBuffer.remaining();
			//FloatArrays.fillFast(fBuffer.array(), ao + pos, ao + pos + rem, e);
			Arrays.fill(fBuffer.array(), ao + pos, ao + pos + rem, e);
			fBuffer.position(pos + rem);
		}
		else{
			int remaining = fBuffer.remaining();
			while (remaining-- > 0)fBuffer.put(e);
		}
	}

	public void put(int x, int y, int z, float f){
		fBuffer.put(index(x,y,z),f);
	}

	public float get(int x, int y, int z){
		return fBuffer.get(index(x,y,z));
	}

	private int index(int x, int y, int z){
		return x + y*xDim + z*xDim*yDim;
	}

}