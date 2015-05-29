package com.tns;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InvalidClassException;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.tns.bindings.ProxyGenerator;
import com.tns.multidex.MultiDex;

import dalvik.system.DexClassLoader;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

public class DexFactory
{
	private Context context;

	private static final String SECONDARY_DEX_FOLDER_NAME = "code_cache" + File.separator + "secondary-dexes";

	private String dexPath;
	private String odexPath;

	private ProxyGenerator proxyGenerator;

	private HashMap<String, Class<?>> injectedProxyClasses = new HashMap<String, Class<?>>();
	
	private String proxyThumb;
	
	public DexFactory(Context context)
	{
		this.context = context;

		ApplicationInfo applicationInfo = context.getApplicationInfo();
		dexPath = applicationInfo.dataDir + File.separator + SECONDARY_DEX_FOLDER_NAME + File.separator;
		odexPath = applicationInfo.dataDir + File.separator + SECONDARY_DEX_FOLDER_NAME + File.separator + "odex" + File.separator;
		proxyGenerator = new ProxyGenerator(dexPath);
		ProxyGenerator.IsLogEnabled = Platform.IsLogEnabled;
		File dexDir = new File(dexPath);
		
		updateProxyThumbAndPurgeCachedProxies(dexDir);
		proxyGenerator.setProxyThumb(proxyThumb);
	}

	static long totalGenTime = 0;
	static long totalMultiDexTime = 0;
	static long totalLoadDexTime = 0;

	public Class<?> resolveClass(String name, String className, String[] methodOverrides) throws ClassNotFoundException, IOException
	{
		if(className.contains("NativeScriptActivity"))
		{
			// Do not extend NativeScriptActivity - it is already extended
			return NativeScriptActivity.class;
		}
		
		String fullClassName = className.replace("$", "_") + "-" + name;
		Class<?> existingClass = injectedProxyClasses.get(fullClassName); 
		if(existingClass != null)
		{
			return existingClass;
		}

		File proxyFile = getProxyFile(fullClassName);
		
		if (proxyFile == null)
		{
			long startGenTime = System.nanoTime();
			String proxyFilePath = "";

			if (Platform.IsLogEnabled)
			{
				Log.d(Platform.DEFAULT_LOG_TAG, "generating proxy in place");
			}
			proxyFilePath = generateProxy(name, className, methodOverrides);
			proxyFile = new File(proxyFilePath);
			long stopGenTime = System.nanoTime();
			totalGenTime += stopGenTime - startGenTime;
			if (Platform.IsLogEnabled)
			{
				Log.d(Platform.DEFAULT_LOG_TAG, "Finished inplace gen took: " + (stopGenTime - startGenTime) / 1000000.0 + "ms");
				Log.d(Platform.DEFAULT_LOG_TAG, "TotalGenTime:  " + totalGenTime / 1000000.0 + "ms");
			}
		}
		String dexFilePath = proxyFile.getPath().replace(".dex", "");
		String jarFilePath = dexFilePath + ".jar";
		File jarFile = new File(jarFilePath);
		
		Class<?> result = null;
		
		if(!jarFile.exists())
		{
			FileOutputStream jarFileStream = new FileOutputStream(jarFile);
			
			ZipOutputStream out = new ZipOutputStream(jarFileStream);
		    out.putNextEntry(new ZipEntry("classes.dex"));
		    byte[] dexData = new byte[(int)proxyFile.length()];
		    FileInputStream fi = new FileInputStream(proxyFile);
		    fi.read(dexData, 0, dexData.length);
		    fi.close();
		    out.write(dexData);
		    out.closeEntry();
		    out.close();
		}
		
		DexClassLoader dexClassLoader = new DexClassLoader(jarFilePath, odexPath, null, this.context.getClassLoader());
		result = dexClassLoader.loadClass(fullClassName);
		
		injectedProxyClasses.put(fullClassName, result);
			
		return result;
	}
	
	public Class<?> findClass(String className) throws ClassNotFoundException
	{
		String canonicalName = className.replace('/', '.');
		return context.getClassLoader().loadClass(canonicalName);
	}

	public static String strJoin(String[] array, String separator)
	{
		if (array == null)
		{
			return "";
		}

		StringBuilder sbStr = new StringBuilder();
		for (int i = 0, il = array.length; i < il; i++)
		{
			if (i > 0)
			{
				sbStr.append(separator);
			}
			sbStr.append(array[i]);
		}
		return sbStr.toString();
	}

	private File getProxyFile(String className) throws InvalidClassException
	{
		String classToProxy = className;

		if (className.startsWith("com.tns.gen"))
		{
			classToProxy = className.substring(12);
		}

		if (classToProxy.startsWith("com.tns.gen"))
		{
			throw new InvalidClassException("Can't generate proxy of proxy");
		}

		String classToProxyFile = classToProxy.replace("$", "_");
		
		if (proxyThumb != null)
		{
			classToProxyFile += "-" + proxyThumb;
		}
		
		String proxyFilePath = dexPath + classToProxyFile + ".dex";
		File proxyFile = new File(proxyFilePath);
		if (proxyFile.exists())
		{
			if (Platform.IsLogEnabled)
			{
				Log.d(Platform.DEFAULT_LOG_TAG, "Looking for proxy file: " + proxyFilePath + " Result: proxy file Found. ClassName: " + className);
			}
			return proxyFile;
		}

		if (Platform.IsLogEnabled)
		{
			Log.d(Platform.DEFAULT_LOG_TAG, "Looking for proxy file: " + proxyFilePath + " Result: NOT Found. Proxy Gen needed. ClassName: " + className);
		}
		return null;
	}

	private String generateProxy(String proxyName, String className, String[] methodOverrides) throws ClassNotFoundException, IOException
	{
		String classToProxyName = className;
		if (className.startsWith("com.tns.gen."))
		{
			classToProxyName = className.substring(12);
		}

		if (classToProxyName.startsWith("com.tns.gen."))
		{
			throw new InvalidClassException("Can't generate proxy of proxy");
		}

		Class<?> classToProxy = Class.forName(classToProxyName);

		HashSet<String> methodOverridesSet = null;
		if (methodOverrides != null)
		{
			methodOverridesSet = new HashSet<String>();
			for (int i = 0; i < methodOverrides.length; i++)
			{
				String methodOverride = methodOverrides[i];
				methodOverridesSet.add(methodOverride);
			}
		}

		return proxyGenerator.generateProxy(proxyName, classToProxy, methodOverridesSet);
	}
	
	private void updateProxyThumbAndPurgeCachedProxies(File proxyDir)
	{
		proxyThumb = generateProxyThumb();
		if (proxyThumb == null)
		{
			throw new RuntimeException("Error generating proxy thumb 1");
		}
		
		String oldProxyThumb = getCachedProxyThumb(proxyDir);
		if (proxyThumb.equals(oldProxyThumb)) 
		{
			return;
		}
		
		if (oldProxyThumb != null)
		{
			purgeProxiesByThumb(oldProxyThumb, proxyDir);
			purgeProxiesByThumb(oldProxyThumb, new File(odexPath));
		}
		else
		{
			//purge all dex files in proxy dir if no thumb file is found
			purgeProxiesByThumb("", proxyDir);
			purgeProxiesByThumb("", new File(odexPath));
		}
		
		saveNewProxyThumb(proxyThumb, proxyDir);
	}

	private void saveNewProxyThumb(String newProxyThumb, File proxyDir)
	{
		File cachedThumbFile = new File(proxyDir, "proxyThumb");
		try
		{
			FileOutputStream out = new FileOutputStream(cachedThumbFile, false);
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
			try
			{
				writer.write(newProxyThumb);
				writer.newLine();
				writer.flush();
			}
			finally
			{
				writer.close();
				out.close();
			}
		}
		catch (FileNotFoundException e)
		{
			Log.e(Platform.DEFAULT_LOG_TAG, "Error while writting current proxy thumb");
			e.printStackTrace();
		}
		catch (IOException e)
		{
			Log.e(Platform.DEFAULT_LOG_TAG, "Error while writting current proxy thumb");
			e.printStackTrace();
		}
	}

	private String generateProxyThumb()
	{
		try
		{
			PackageInfo packageInfo = this.context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			int code = packageInfo.versionCode;
			long updateTime = packageInfo.lastUpdateTime;
			return String.valueOf(updateTime) + "-" + String.valueOf(code);
		}
		catch (PackageManager.NameNotFoundException e)
		{
			Log.e(Platform.DEFAULT_LOG_TAG, "Error while getting current proxy thumb");
			e.printStackTrace();
		}
		
		return null;
	}
	
	private void purgeProxiesByThumb(String cachedproxyThumb, File pathToPurge)
	{
	    if (!pathToPurge.isDirectory())
	    {
	    	throw new RuntimeException("Purge path not a directory");
	    }
	    
        String[] children = pathToPurge.list();
        
        for (int i = 0; i < children.length; i++)
        {
        	String filename = children[i];
        	File purgeCandidate = new File(pathToPurge, filename);
            if (purgeCandidate.isDirectory())
            {
            	purgeProxiesByThumb(cachedproxyThumb, purgeCandidate);
            }
            else
            {
            	if (cachedproxyThumb != "" && !filename.contains(cachedproxyThumb))
            	{
            		continue;
            	}
            	
                boolean b = purgeCandidate.delete();
                if (b == false)
                {
                    Log.e(Platform.DEFAULT_LOG_TAG, "Error purging cached proxy file: " + purgeCandidate.getAbsolutePath());
                }
            }
        }
	}
	
	private String getCachedProxyThumb(File proxyDir)
	{
		try
		{
			File cachedThumbFile = new File(proxyDir, "proxyThumb");
			if (cachedThumbFile.exists())
			{
				FileInputStream in = new FileInputStream(cachedThumbFile);
				BufferedReader reader = new BufferedReader(new InputStreamReader(in));
				String cachedThumb = reader.readLine();
				reader.close();
				in.close();
				return cachedThumb;
			}
		}
		catch (FileNotFoundException e)
		{
			Log.e(Platform.DEFAULT_LOG_TAG, "Error while getting current proxy thumb");
			e.printStackTrace();
		}
		catch (IOException e)
		{
			Log.e(Platform.DEFAULT_LOG_TAG, "Error while getting current proxy thumb");
			e.printStackTrace();
		}
		
		return null;
	}
}
