package tech.fadedib.firebasestorage_multiplefilesupload;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tech.fadedib.firebasestorage_multiplefilesupload.Helpers.CoreHelper;
import tech.fadedib.firebasestorage_multiplefilesupload.Helpers.CustomModel;
import tech.fadedib.firebasestorage_multiplefilesupload.Helpers.ImagesAdapter;

public class MainActivity extends AppCompatActivity {

    private static final int READ_PERMISSION_CODE = 1;
    private static final int PICK_IMAGE_REQUEST_CODE = 2;
    ImageView no_images;
    FloatingActionButton btnPickImages, btnUploadImages;
    RecyclerView recyclerView;
    List<CustomModel> imagesList;
    List<String> savedImagesUri;
    ImagesAdapter adapter;
    CoreHelper coreHelper;
    FirebaseStorage storage;
    FirebaseFirestore firestore;
    CollectionReference reference;
    int counter;
    Uri photoURI;

    String imageEncoded;
    List<String> imagesEncodedList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        firestore = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        reference = firestore.collection("user");

        savedImagesUri = new ArrayList<>();

        no_images = findViewById(R.id.no_image);
        btnPickImages = findViewById(R.id.fabChooseImage);
        btnUploadImages = findViewById(R.id.fabUploadImage);
        imagesList = new ArrayList<>();
        coreHelper = new CoreHelper(this);
        //Code to show list of images start
        recyclerView = findViewById(R.id.recyclerView);
        adapter = new ImagesAdapter(this, imagesList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                if (adapter.getItemCount() != 0) {
                    no_images.setVisibility(View.GONE);
                } else {
                    no_images.setVisibility(View.VISIBLE);
                }
            }
        });
        //Code to show list of images end
        btnPickImages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                verifyPermissionAndPickImage();
            }
        });
        btnUploadImages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    uploadImages(view);
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    private void uploadImages(View view) throws URISyntaxException {
        if (imagesList != null) {
            if (imagesList.size() != 0) {
                final ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setMessage("Uploaded 0/" + imagesList.size());
                progressDialog.setCanceledOnTouchOutside(false); //Remove this line if you want your user to be able to cancel upload
                progressDialog.setCancelable(false);    //Remove this line if you want your user to be able to cancel upload
                progressDialog.show();
                final StorageReference storageReference = storage.getReference();
                for (int i = 0; i < imagesList.size(); i++) {
                    Log.d("TAG", "uploadImages: " + imagesList.get(i).getImageURI());
                    final int finalI = i;

                    String[] filePathColumn = {MediaStore.Images.Media.DATA};
                    // Get the cursor
                    Cursor cursor = this.getContentResolver().query(imagesList.get(i).getImageURI(), filePathColumn, null, null, null);
                    // Move to first row
                    cursor.moveToFirst();
                    //Get the column index of MediaStore.Images.Media.DATA
                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    //Gets the String value in the column
                    String imgDecodableString = cursor.getString(columnIndex);
                    cursor.close();


                    File fileImage = new File(imgDecodableString);
                    Bitmap bmp = BitmapFactory.decodeFile(fileImage.getAbsolutePath());
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 20, baos);
                    byte[] data = baos.toByteArray();

                    storageReference.child("userData/").child(imagesList.get(i).getImageName()).putBytes(data).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                            if (task.isSuccessful()) {
                                storageReference.child("userData/").child(imagesList.get(finalI).getImageName()).getDownloadUrl().addOnCompleteListener(new OnCompleteListener<Uri>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Uri> task) {
                                        counter++;
                                        progressDialog.setMessage("Uploaded " + counter + "/" + imagesList.size());
                                        if (task.isSuccessful()) {
                                            savedImagesUri.add(task.getResult().toString());
                                        } else {
                                            storageReference.child("userData/").child(imagesList.get(finalI).getImageName()).delete();
                                            Toast.makeText(MainActivity.this, "Couldn't save " + imagesList.get(finalI).getImageName(), Toast.LENGTH_SHORT).show();
                                        }
                                        if (counter == imagesList.size()) {
                                            saveImageDataToFirestore(progressDialog);
                                        }
                                    }
                                });
                            } else {
                                progressDialog.setMessage("Uploaded " + counter + "/" + imagesList.size());
                                counter++;
                                Toast.makeText(MainActivity.this, "Couldn't upload " + imagesList.get(finalI).getImageName(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            } else {
                //coreHelper.createSnackBar(view, "Please add some images first.", "", null, Snackbar.LENGTH_SHORT);
            }
        }
    }

    private void saveImageDataToFirestore(final ProgressDialog progressDialog) {
        progressDialog.setMessage("Saving uploaded images...");
        progressDialog.cancel();
        Map<String, String> dataMap = new HashMap<>();
        for (int i = 0; i < savedImagesUri.size(); i++) {
            dataMap.put("image" + i, savedImagesUri.get(i));
        }
        reference.add(dataMap).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
            @Override
            public void onSuccess(DocumentReference documentReference) {
                progressDialog.dismiss();
                coreHelper.createAlert("Success", "Images uploaded and saved successfully!", "OK", "", null, null, null);

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                progressDialog.dismiss();
                coreHelper.createAlert("Error", "Images uploaded but we couldn't save them to database.", "OK", "", null, null, null);
                Log.e("MainActivity:SaveData", e.getMessage());
            }
        });
    }

    private void verifyPermissionAndPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                //If permission is granted
                pickImage();
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_PERMISSION_CODE);
            }
        } else {
            //no need to check permissions in android versions lower then marshmallow
            pickImage();
        }
    }

    private void pickImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setAction(Intent.ACTION_PICK);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case READ_PERMISSION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pickImage();
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST_CODE) {
            imagesEncodedList = new ArrayList<>();
            if (resultCode == RESULT_OK && data != null) {
                ClipData clipData = data.getClipData();
                Log.d("Clipdata", "onActivityResult: " + clipData);
                if (clipData != null) {
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        Uri uri = clipData.getItemAt(i).getUri();

                        Log.d("imageEncoded", "onActivityResult: " + imagesEncodedList);

                        imagesList.add(new CustomModel(coreHelper.getFileNameFromUri(uri), uri));
                        adapter.notifyDataSetChanged();
                    }
                } else {
                    Uri uri = data.getData();
                    Log.d("Clipdata", "onActivityResult: " + uri);
                    imagesList.add(new CustomModel(coreHelper.getFileNameFromUri(uri), uri));
                    adapter.notifyDataSetChanged();
                }
            }
        }
    }
}
