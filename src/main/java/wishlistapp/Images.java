package wishlistapp;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javafx.scene.image.Image;

/** Копия изображения хранится в data/images; в БД — относительный путь. */
public final class Images {
    private Images(){}
    public static String importFile(File file)throws IOException{
        if(file==null)return null;
        String name=file.getName().toLowerCase(Locale.ROOT);
        String ext=name.endsWith(".png")?".png":name.endsWith(".jpg")?".jpg":name.endsWith(".jpeg")?".jpeg":null;
        if(ext==null)throw new IOException("Выберите PNG или JPEG");
        if(Files.size(file.toPath())>20L*1024*1024)throw new IOException("Изображение должно быть не больше 20 МБ");
        Image test=new Image(file.toURI().toString(),160,160,true,true,false);
        if(test.isError())throw new IOException("Не удалось прочитать изображение");
        Path dir=Paths.get("data","images");Files.createDirectories(dir);
        Path target=dir.resolve(UUID.randomUUID().toString()+ext);
        Files.copy(file.toPath(),target);
        return target.toString().replace(File.separatorChar,'/');
    }
    public static Image load(String path){
        try{
            if(path!=null&&!path.trim().isEmpty()){
                Path p=Paths.get(path);
                if(Files.isRegularFile(p)){
                    Image i=new Image(p.toUri().toString(),160,160,true,true,false);
                    if(!i.isError())return i;
                }
            }
        }catch(RuntimeException ignored){}
        return new Image(Images.class.getResourceAsStream("/images/placeholder.png"),160,160,true,true);
    }
}
