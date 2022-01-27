package ir.mahdiparastesh.instatools.data

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class Account(
    @PrimaryKey(autoGenerate = false) var id: Long,
    var user: String? = null,
    var name: String? = null,
    var photo: String? = null,
    var folder: String? = null
) : Parcelable {
    @Suppress("unused")
    constructor() : this(0, "", "", null, null)

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString()
    )

    class Sort : Comparator<Account> {
        override fun compare(a: Account, b: Account) =
            (a.name ?: "").compareTo(b.name ?: "")
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(user)
        parcel.writeString(name)
        parcel.writeString(photo)
        parcel.writeString(folder)
    }

    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<Account> {
        override fun createFromParcel(parcel: Parcel): Account = Account(parcel)

        override fun newArray(size: Int): Array<Account?> = arrayOfNulls(size)
    }
}
